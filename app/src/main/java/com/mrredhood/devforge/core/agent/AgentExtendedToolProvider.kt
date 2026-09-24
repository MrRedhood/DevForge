package com.mrredhood.devforge.core.agent

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import com.mrredhood.devforge.core.build.BuildConfiguration
import com.mrredhood.devforge.core.build.BuildTarget
import com.mrredhood.devforge.core.github.GitHubActionsGateway
import com.mrredhood.devforge.core.github.GitHubArtifactsResult
import com.mrredhood.devforge.core.github.GitHubLogsResult
import com.mrredhood.devforge.core.github.GitHubWorkflowRunsResult
import com.mrredhood.devforge.core.git.GitDetectionState
import com.mrredhood.devforge.core.git.GitDiffService
import com.mrredhood.devforge.core.git.GitRemoteResult
import com.mrredhood.devforge.core.git.GitRemoteTransportService
import com.mrredhood.devforge.core.git.GitRepositoryService
import com.mrredhood.devforge.core.git.GitWorkspaceStatusService
import com.mrredhood.devforge.core.policy.Capability
import com.mrredhood.devforge.core.policy.RiskLevel
import com.mrredhood.devforge.core.security.CredentialSecurityStore
import com.mrredhood.devforge.core.storage.ApprovalRepository
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.storage.DurableStateRepository
import com.mrredhood.devforge.core.terminal.TerminalCapability
import com.mrredhood.devforge.core.terminal.TerminalCapabilityResult
import com.mrredhood.devforge.core.terminal.TerminalCommandParser
import com.mrredhood.devforge.core.terminal.TerminalCommandPolicy
import com.mrredhood.devforge.core.workspace.GitHubWorkspaceStore
import com.mrredhood.devforge.core.workspace.WorkspaceFileOperations
import com.mrredhood.devforge.core.workspace.WorkspaceFileTree
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import org.json.JSONArray
import org.json.JSONObject

class AgentExtendedToolProvider(context: Context) {
    private val app = context.applicationContext
    private val db = DevForgeDatabase.get(app)
    private val resolver = app.contentResolver
    private val tree = WorkspaceFileTree(resolver)
    private val files = WorkspaceFileOperations(resolver)
    private val githubStore = GitHubWorkspaceStore(app)
    private val actionsGateway = GitHubActionsGateway.forBuildStore(CredentialSecurityStore(app))
    private val github = com.mrredhood.devforge.core.github.GitHubRepositoryGateway(CredentialSecurityStore(app))
    private val gitRepo = GitRepositoryService(resolver)
    private val gitStatus = GitWorkspaceStatusService(resolver)
    private val gitDiff = GitDiffService(resolver)
    private val gitRemote = GitRemoteTransportService(app)
    private val terminal = TerminalCapability(app, ApprovalRepository(db.approvalDao()), DurableStateRepository(db), com.mrredhood.devforge.core.policy.PermissionMode.AUTONOMOUS)
    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processes = ConcurrentHashMap<String, ManagedProcess>()
    private val checkpointRoot = File(app.filesDir, "ai-checkpoints")

    private val specs = listOf(
        AgentToolId.COPY_FILE, AgentToolId.COPY_FOLDER, AgentToolId.REPLACE_TEXT, AgentToolId.INSERT_TEXT,
        AgentToolId.DELETE_TEXT, AgentToolId.REPLACE_RANGE, AgentToolId.FORMAT_FILE, AgentToolId.ORGANIZE_IMPORTS,
        AgentToolId.FIND_SYMBOL, AgentToolId.FIND_REFERENCES, AgentToolId.ANALYZE_WORKSPACE, AgentToolId.GET_PROJECT_INFO,
        AgentToolId.GET_PROJECT_STRUCTURE, AgentToolId.FIND_ENTRY_POINTS, AgentToolId.FIND_CONFIG_FILES, AgentToolId.GET_DEPENDENCIES,
        AgentToolId.GET_BUILD_TARGETS, AgentToolId.BUILD_PROJECT, AgentToolId.CLEAN_PROJECT, AgentToolId.RUN_TESTS, AgentToolId.RUN_TEST,
        AgentToolId.RUN_LINT, AgentToolId.INSPECT_BUILD_ERROR, AgentToolId.GET_BUILD_OUTPUT, AgentToolId.RUN_BACKGROUND_COMMAND,
        AgentToolId.GET_PROCESS_STATUS, AgentToolId.STOP_PROCESS, AgentToolId.LIST_PROCESSES, AgentToolId.READ_PROCESS_OUTPUT,
        AgentToolId.GIT_STATUS, AgentToolId.GIT_DIFF, AgentToolId.GIT_DIFF_FILE, AgentToolId.GIT_ADD, AgentToolId.GIT_RESTORE,
        AgentToolId.GIT_COMMIT, AgentToolId.GIT_SHOW_COMMIT, AgentToolId.GIT_CREATE_TAG, AgentToolId.GIT_STASH, AgentToolId.GIT_RESET,
        AgentToolId.GITHUB_CREATE_BRANCH, AgentToolId.GITHUB_CREATE_PR, AgentToolId.GITHUB_CREATE_ISSUE, AgentToolId.GITHUB_COMMENT_ISSUE,
        AgentToolId.GITHUB_LIST_ISSUES, AgentToolId.GITHUB_GET_ACTIONS, AgentToolId.GITHUB_GET_WORKFLOW_LOGS, AgentToolId.GITHUB_DISPATCH_WORKFLOW,
        AgentToolId.GITHUB_GET_ARTIFACT, AgentToolId.ANDROID_LOGCAT, AgentToolId.ANDROID_BUILD_APK, AgentToolId.ANDROID_BUILD_AAB,
        AgentToolId.ANDROID_INSTALL_APK, AgentToolId.ANDROID_LAUNCH_APP, AgentToolId.ANDROID_GET_DEVICE_INFO,
        AgentToolId.CREATE_CHECKPOINT, AgentToolId.RESTORE_CHECKPOINT, AgentToolId.UNDO_AI_CHANGE, AgentToolId.COMPARE_WORKSPACE,
    )

    fun registerAll(registry: AgentToolRegistry): AgentToolRegistry {
        specs.forEach { id -> registry.register(Tool(id)) }
        return registry
    }

    private inner class Tool(private val id: AgentToolId) : AgentTool {
        override val definition = AgentToolDefinition(id, description(id), capability(id), risk(id), sideEffecting(id))

        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult {
            currentCoroutineContext().ensureActive()
            val args = JSONObject(request.argumentsJson)
            return try {
                when (id) {
                    AgentToolId.COPY_FILE -> copyFile(context, args)
                    AgentToolId.COPY_FOLDER -> copyFolder(context, args)
                    AgentToolId.REPLACE_TEXT -> editText(context, args, "replace")
                    AgentToolId.INSERT_TEXT -> editText(context, args, "insert")
                    AgentToolId.DELETE_TEXT -> editText(context, args, "delete")
                    AgentToolId.REPLACE_RANGE -> editRange(context, args)
                    AgentToolId.FORMAT_FILE, AgentToolId.ORGANIZE_IMPORTS -> formatFile(context, args)
                    AgentToolId.FIND_SYMBOL -> searchContent(context, args, true)
                    AgentToolId.FIND_REFERENCES -> searchContent(context, args, false)
                    AgentToolId.ANALYZE_WORKSPACE, AgentToolId.GET_PROJECT_INFO -> projectInfo(context)
                    AgentToolId.GET_PROJECT_STRUCTURE -> projectTree(context)
                    AgentToolId.FIND_ENTRY_POINTS -> findNames(context, setOf("AndroidManifest.xml","MainActivity.kt","Application.kt","main.py","main.go","main.rs","index.js","index.ts"))
                    AgentToolId.FIND_CONFIG_FILES -> findNames(context, setOf("settings.gradle","settings.gradle.kts","build.gradle","build.gradle.kts","gradle.properties","package.json","pyproject.toml","requirements.txt","Cargo.toml","go.mod","pom.xml","Makefile","tsconfig.json"))
                    AgentToolId.GET_DEPENDENCIES -> dependencies(context)
                    AgentToolId.GET_BUILD_TARGETS -> AgentToolResult.Success("Available build targets.", BuildTarget.entries.joinToString("\n") { it.workflowInput + ": " + it.description })
                    AgentToolId.BUILD_PROJECT, AgentToolId.RUN_TESTS, AgentToolId.RUN_TEST, AgentToolId.RUN_LINT,
                    AgentToolId.ANDROID_BUILD_APK, AgentToolId.ANDROID_BUILD_AAB, AgentToolId.GITHUB_DISPATCH_WORKFLOW -> dispatch(args, id)
                    AgentToolId.CLEAN_PROJECT -> cleanProject(context)
                    AgentToolId.INSPECT_BUILD_ERROR -> inspectError(args)
                    AgentToolId.GET_BUILD_OUTPUT, AgentToolId.GITHUB_GET_WORKFLOW_LOGS -> workflowLogs(args)
                    AgentToolId.RUN_BACKGROUND_COMMAND -> startProcess(context, args)
                    AgentToolId.GET_PROCESS_STATUS -> processStatus(args)
                    AgentToolId.STOP_PROCESS -> stopProcess(args)
                    AgentToolId.LIST_PROCESSES -> listProcesses()
                    AgentToolId.READ_PROCESS_OUTPUT -> processOutput(args)
                    AgentToolId.GIT_STATUS -> gitStatus(context)
                    AgentToolId.GIT_DIFF, AgentToolId.GIT_DIFF_FILE -> gitDiff(context, args)
                    AgentToolId.GIT_ADD, AgentToolId.GIT_COMMIT, AgentToolId.GIT_CREATE_TAG -> gitSync(context, id, args)
                    AgentToolId.GIT_RESTORE, AgentToolId.GIT_RESET, AgentToolId.UNDO_AI_CHANGE, AgentToolId.RESTORE_CHECKPOINT -> restoreCheckpoint(context, args)
                    AgentToolId.GIT_STASH -> createCheckpoint(context)
                    AgentToolId.GIT_SHOW_COMMIT -> AgentToolResult.Failure("Use get_git_log for bounded commit history.")
                    AgentToolId.GITHUB_CREATE_BRANCH -> createBranch(context, args)
                    AgentToolId.GITHUB_CREATE_PR -> createPr(context, args)
                    AgentToolId.GITHUB_CREATE_ISSUE -> createIssue(context, args)
                    AgentToolId.GITHUB_COMMENT_ISSUE -> commentIssue(context, args)
                    AgentToolId.GITHUB_LIST_ISSUES -> listIssues(context)
                    AgentToolId.GITHUB_GET_ACTIONS -> listGitHubActions(args)
                    AgentToolId.GITHUB_GET_ARTIFACT -> artifacts(args)
                    AgentToolId.ANDROID_LOGCAT -> logcat(args)
                    AgentToolId.ANDROID_GET_DEVICE_INFO -> deviceInfo()
                    AgentToolId.ANDROID_INSTALL_APK -> AgentToolResult.Failure("APK installation uses the Android package-installer UI.")
                    AgentToolId.ANDROID_LAUNCH_APP -> AgentToolResult.Failure("App launch requires an attached device bridge.")
                    AgentToolId.CREATE_CHECKPOINT -> createCheckpoint(context)
                    AgentToolId.COMPARE_WORKSPACE -> compareCheckpoint(context, args)
                    else -> AgentToolResult.Failure("Unsupported extended tool: " + id.wireName)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                AgentToolResult.Failure(error.message ?: (id.wireName + " failed."))
            }
        }

        override suspend fun mutationPaths(context: AgentToolContext, request: AgentToolRequest): List<String> {
            val a = runCatching { JSONObject(request.argumentsJson) }.getOrNull() ?: return emptyList()
            return when (id) {
                AgentToolId.COPY_FILE, AgentToolId.COPY_FOLDER -> listOfNotNull(a.optString("path").takeIf { it.isNotBlank() }, a.optString("destination").takeIf { it.isNotBlank() })
                AgentToolId.REPLACE_TEXT, AgentToolId.INSERT_TEXT, AgentToolId.DELETE_TEXT, AgentToolId.REPLACE_RANGE,
                AgentToolId.FORMAT_FILE, AgentToolId.ORGANIZE_IMPORTS -> listOfNotNull(a.optString("path").takeIf { it.isNotBlank() })
                else -> emptyList()
            }
        }
    }

    private fun description(id: AgentToolId): String = id.wireName.replace('_',' ') + " operational tool."
    private fun capability(id: AgentToolId): Capability = when (id) {
        AgentToolId.BUILD_PROJECT, AgentToolId.RUN_TESTS, AgentToolId.RUN_TEST, AgentToolId.RUN_LINT,
        AgentToolId.GITHUB_DISPATCH_WORKFLOW, AgentToolId.ANDROID_BUILD_APK, AgentToolId.ANDROID_BUILD_AAB -> Capability.DISPATCH_BUILD
        AgentToolId.RUN_BACKGROUND_COMMAND, AgentToolId.GET_PROCESS_STATUS, AgentToolId.STOP_PROCESS, AgentToolId.LIST_PROCESSES, AgentToolId.READ_PROCESS_OUTPUT,
        AgentToolId.ANDROID_LAUNCH_APP -> Capability.RUN_TERMINAL
        AgentToolId.GITHUB_CREATE_BRANCH -> Capability.CREATE_BRANCH
        AgentToolId.GITHUB_CREATE_PR, AgentToolId.GITHUB_CREATE_ISSUE, AgentToolId.GITHUB_COMMENT_ISSUE -> Capability.PUSH_REMOTE
        AgentToolId.GIT_ADD -> Capability.STAGE_FILES
        AgentToolId.GIT_COMMIT, AgentToolId.GIT_CREATE_TAG -> Capability.CREATE_COMMIT
        AgentToolId.GIT_RESTORE, AgentToolId.GIT_RESET, AgentToolId.RESTORE_CHECKPOINT, AgentToolId.UNDO_AI_CHANGE -> Capability.EDIT_FILES
        else -> if (id.name.contains("BUILD") || id.name.contains("TEST") || id.name.contains("LINT")) Capability.RUN_CHECK else Capability.READ_WORKSPACE
    }
    private fun risk(id: AgentToolId): RiskLevel = when (id) {
        AgentToolId.GIT_RESET, AgentToolId.RESTORE_CHECKPOINT, AgentToolId.UNDO_AI_CHANGE, AgentToolId.GITHUB_CREATE_PR, AgentToolId.GITHUB_CREATE_BRANCH -> RiskLevel.R4
        AgentToolId.GIT_ADD, AgentToolId.GIT_COMMIT, AgentToolId.GIT_CREATE_TAG, AgentToolId.GITHUB_CREATE_ISSUE, AgentToolId.GITHUB_COMMENT_ISSUE,
        AgentToolId.BUILD_PROJECT, AgentToolId.RUN_TESTS, AgentToolId.RUN_TEST, AgentToolId.RUN_LINT,
        AgentToolId.ANDROID_BUILD_APK, AgentToolId.ANDROID_BUILD_AAB, AgentToolId.GITHUB_DISPATCH_WORKFLOW -> RiskLevel.R3
        AgentToolId.GIT_RESTORE, AgentToolId.GIT_STASH, AgentToolId.STOP_PROCESS, AgentToolId.RUN_BACKGROUND_COMMAND -> RiskLevel.R3
        else -> RiskLevel.R0
    }
    private fun sideEffecting(id: AgentToolId): Boolean = risk(id) >= RiskLevel.R2 || id.name.startsWith("COPY") || id.name in setOf("REPLACE_TEXT","INSERT_TEXT","DELETE_TEXT","REPLACE_RANGE","FORMAT_FILE","ORGANIZE_IMPORTS")

    private suspend fun root(id: String): Uri = Uri.parse(db.workspaceDao().findById(id)?.treeUri ?: error("Workspace not found."))
    private suspend fun resolveRoot(workspaceId: String, path: String): Uri {
        var current = root(workspaceId)
        path.trim('/').split('/').filter { it.isNotBlank() }.forEach { part ->
            current = tree.list(current, 500).firstOrNull { it.name == part }?.uri ?: error("Path does not exist: " + path)
        }
        return current
    }
    private suspend fun readText(workspaceId: String, path: String): String {
        val uri = resolveRoot(workspaceId, path)
        return runInterruptible(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { it.readBytes() }?.toString(Charsets.UTF_8)
                ?: error("Unable to read " + path)
        }
    }
    private suspend fun writeText(workspaceId: String, path: String, value: String) {
        require(value.toByteArray(Charsets.UTF_8).size <= 512 * 1024)
        val uri = resolveRoot(workspaceId, path)
        runInterruptible(Dispatchers.IO) {
            resolver.openOutputStream(uri, "wt")?.use { it.write(value.toByteArray(Charsets.UTF_8)) }
                ?: error("Unable to write " + path)
        }
    }
    private suspend fun copyFile(context: AgentToolContext, args: JSONObject): AgentToolResult {
        val source = args.optString("path").trim()
        val destination = args.optString("destination").trim()
        DocumentsContract.copyDocument(resolver, resolveRoot(context.workspaceId, source), resolveRoot(context.workspaceId, destination))
            ?: error("Copy is not supported by the active document provider.")
        return AgentToolResult.Success("Copied file.", affectedPaths = listOf(source, destination))
    }
    private suspend fun copyFolder(context: AgentToolContext, args: JSONObject): AgentToolResult {
        val source = resolveRoot(context.workspaceId, args.optString("path"))
        val destination = resolveRoot(context.workspaceId, args.optString("destination"))
        val name = documentName(source) ?: args.optString("path").substringAfterLast('/')
        val target = files.createFolder(destination, args.optString("newName").trim().ifBlank { name })
        tree.list(source, 500).forEach { child -> if (child.isDirectory) copyDirectory(child.uri,target) else DocumentsContract.copyDocument(resolver,child.uri,target) }
        return AgentToolResult.Success("Copied folder.", affectedPaths = listOf(args.optString("path"), args.optString("destination")))
    }
    private fun copyDirectory(source: Uri, targetParent: Uri) {
        val folder = files.createFolder(targetParent, documentName(source) ?: "folder")
        tree.list(source,500).forEach { child -> if (child.isDirectory) copyDirectory(child.uri,folder) else DocumentsContract.copyDocument(resolver,child.uri,folder) }
    }
    private suspend fun editText(context: AgentToolContext,args:JSONObject,mode:String):AgentToolResult{
        val path=args.optString("path").trim();val source=readText(context.workspaceId,path)
        val updated=when(mode){
            "replace"->{val old=args.optString("old");require(old.isNotEmpty()&&source.contains(old));source.replace(old,args.optString("new"),ignoreCase=false)}
            "insert"->{val offset=args.optInt("offset",-1);require(offset in 0..source.length);source.substring(0,offset)+args.optString("text")+source.substring(offset)}
            else->{val start=args.optInt("start",-1);val end=args.optInt("end",-1);require(start>=0&&end>=start&&end<=source.length);source.removeRange(start,end)}
        }
        writeText(context.workspaceId,path,updated);return AgentToolResult.Success("Updated "+path+".",affectedPaths=listOf(path))
    }
    private suspend fun editRange(context:AgentToolContext,args:JSONObject):AgentToolResult{
        val path=args.optString("path").trim();val lines=readText(context.workspaceId,path).split('\n')
        val start=args.optInt("startLine",-1);val end=args.optInt("endLine",-1);require(start>=1&&end>=start&&end<=lines.size)
        writeText(context.workspaceId,path,(lines.take(start-1)+args.optString("content").split('\n')+lines.drop(end)).joinToString("\n"))
        return AgentToolResult.Success("Replaced lines.",affectedPaths=listOf(path))
    }
    private suspend fun formatFile(context:AgentToolContext,args:JSONObject):AgentToolResult{
        val path=args.optString("path").trim();val source=readText(context.workspaceId,path);val imports=source.lines().filter{it.trimStart().startsWith("import ")}.distinct().sorted()
        val body=source.lines().filterNot{it.trimStart().startsWith("import ")}.map{it.trimEnd()}
        val formatted=(imports+body).joinToString("\n").trimEnd()+"\n";writeText(context.workspaceId,path,formatted)
        return AgentToolResult.Success("Formatted "+path+".",affectedPaths=listOf(path))
    }
    private suspend fun searchContent(context:AgentToolContext,args:JSONObject,declaration:Boolean):AgentToolResult{
        val query=args.optString("symbol").trim();require(query.isNotBlank())
        val hits=mutableListOf<String>()
        suspend fun visit(uri:Uri,prefix:String,depth:Int){if(depth>12||hits.size>=400)return;tree.list(uri,500).forEach{e->if(e.name==".git")return@forEach;val path=if(prefix.isBlank())e.name else prefix+"/"+e.name;if(e.isDirectory)visit(e.uri,path,depth+1)else{val text=runCatching{runInterruptible(Dispatchers.IO){resolver.openInputStream(e.uri)?.use{it.readBytes().take(256*1024).toByteArray().toString(Charsets.UTF_8)}}}.getOrNull()?:return@forEach;text.lineSequence().forEachIndexed{i,line->{val re=if(declaration)Regex("\\b(class|object|interface|fun|val|var|const|typealias)\\s+"+Regex.escape(query)+"\\b")else Regex("\\b"+Regex.escape(query)+"\\b");if(re.containsMatchIn(line)&&hits.size<400)hits+=path+":"+(i+1)+": "+line.take(240)}}}}}
        visit(root(context.workspaceId),"",0);return AgentToolResult.Success("Found "+hits.size+" location(s).",output=JSONArray(hits).toString())
    }
    private suspend fun projectInfo(context:AgentToolContext):AgentToolResult=projectTree(context,true)
    private suspend fun projectTree(context:AgentToolContext,summary:Boolean=false):AgentToolResult{
        val root=root(context.workspaceId);val paths=mutableListOf<String>();val langs=linkedSetOf<String>()
        suspend fun visit(uri:Uri,prefix:String,depth:Int){if(depth>10||paths.size>=300)return;tree.list(uri,500).forEach{e->if(e.name==".git")return@forEach;val path=if(prefix.isBlank())e.name else prefix+"/"+e.name;if(e.isDirectory)visit(e.uri,path,depth+1)else{paths+=path;when{e.name.endsWith(".kt")->langs+="Kotlin";e.name.endsWith(".java")->langs+="Java";e.name.endsWith(".py")->langs+="Python";e.name.endsWith(".ts")->langs+="TypeScript";e.name.endsWith(".js")->langs+="JavaScript"}}}}
        visit(root,"",0);val configs=paths.filter{it.substringAfterLast('/') in setOf("settings.gradle.kts","settings.gradle","build.gradle.kts","build.gradle","package.json","pyproject.toml","Cargo.toml","go.mod")}
        return if(summary)AgentToolResult.Success("Workspace analyzed.",output=JSONObject().put("fileCount",paths.size).put("languages",JSONArray(langs.toList())).put("configFiles",JSONArray(configs)).toString()) else AgentToolResult.Success("Project structure.",output=paths.joinToString("\n"))
    }
    private suspend fun findNames(context:AgentToolContext,names:Set<String>):AgentToolResult{
        val result=projectTree(context,false);val text=(result as AgentToolResult.Success).output;val hits=text.lines().filter{it.substringAfterLast('/') in names};return AgentToolResult.Success("Found "+hits.size+" file(s).",output=hits.joinToString("\n"))
    }
    private suspend fun dependencies(context:AgentToolContext):AgentToolResult{
        val candidates=listOf("package.json","requirements.txt","pyproject.toml","Cargo.toml","go.mod","pom.xml","build.gradle.kts","app/build.gradle.kts");val output=StringBuilder()
        candidates.forEach{path->runCatching{readText(context.workspaceId,path)}.onSuccess{t->val lines=t.lines().filter{it.contains("implementation(")||it.contains("api(")||it.contains("testImplementation(")||it.contains("\"dependencies\"")}.take(80);if(lines.isNotEmpty()){output.append("## ").append(path).append('\n');lines.forEach{output.append(it).append('\n')}}}}
        return AgentToolResult.Success("Collected dependency declarations.",output=output.toString().take(40_000))
    }
    private suspend fun dispatch(args:JSONObject,id:AgentToolId):AgentToolResult{
        val owner=args.optString("owner").trim();val repo=args.optString("repository").trim();require(owner.isNotBlank()&&repo.isNotBlank())
        val target=when(id){AgentToolId.ANDROID_BUILD_APK->BuildTarget.DebugApk;AgentToolId.ANDROID_BUILD_AAB->BuildTarget.ReleaseBundle;AgentToolId.RUN_TESTS,AgentToolId.RUN_TEST->BuildTarget.DebugApk;else->when(args.optString("target","debug_apk")){"release_apk"->BuildTarget.ReleaseApk;"release_aab"->BuildTarget.ReleaseBundle;else->BuildTarget.DebugApk}}
        val config=BuildConfiguration(githubOwner=owner,githubRepository=repo,workflowFile=args.optString("workflowFile",".github/workflows/android.yml"),branch=args.optString("branch","main"),buildTask=args.optString("buildTask",target.buildTask),artifactName=target.artifactName,target=target)
        return when(val r=actionsGateway.dispatch(owner,repo,config)){is com.mrredhood.devforge.core.github.GitHubDispatchResult.Success->AgentToolResult.Success("Dispatched "+target.label+" run.",output=JSONObject().put("runId",r.run.id).put("runNumber",r.run.runNumber).put("url",r.run.htmlUrl).toString());is com.mrredhood.devforge.core.github.GitHubDispatchResult.Failure->AgentToolResult.Failure(r.message)}
    }
    private suspend fun cleanProject(context:AgentToolContext):AgentToolResult{val root=root(context.workspaceId);val found=mutableListOf<Uri>();suspend fun visit(uri:Uri,d:Int){if(d>10||found.size>=20)return;tree.list(uri,500).forEach{e->if(e.name==".git")return@forEach;if(e.isDirectory&&e.name=="build")found+=e.uri else if(e.isDirectory)visit(e.uri,d+1)}};visit(root,0);found.forEach{deleteRecursive(it)};return AgentToolResult.Success("Removed "+found.size+" build directory(s).")}
    private suspend fun createCheckpoint(context:AgentToolContext):AgentToolResult{checkpointRoot.mkdirs();val dir=File(checkpointRoot,context.workspaceId).apply{mkdirs()};val id="checkpoint-"+System.currentTimeMillis();File(dir,id+".json").writeText(projectTree(context,false).toString(),Charsets.UTF_8);return AgentToolResult.Success("Created checkpoint "+id+".")}
    private suspend fun compareCheckpoint(context:AgentToolContext,args:JSONObject):AgentToolResult=AgentToolResult.Success("Checkpoint comparison is available through recovery state; checkpointId="+args.optString("checkpointId"))
    private suspend fun restoreCheckpoint(context:AgentToolContext,args:JSONObject):AgentToolResult=AgentToolResult.Failure("Checkpoint restore is intentionally bounded to the recovery service and is not available without a matching checkpoint snapshot.")
}

private data class ManagedProcess(val job:Job,val output:StringBuilder,@Volatile var state:String,val command:String)
