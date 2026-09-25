package com.mrredhood.devforge.core.agent

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.StatFs
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
        AgentToolId.FILE_EXISTS, AgentToolId.DIRECTORY_INFO, AgentToolId.COMPARE_FILES,
        AgentToolId.DETECT_PROJECT_TYPE, AgentToolId.GET_OS_INFO, AgentToolId.GET_RUNTIME_INFO, AgentToolId.GET_STORAGE_INFO,
        AgentToolId.GET_GIT_BRANCH, AgentToolId.GET_GIT_REMOTES,
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
                    AgentToolId.FILE_EXISTS -> fileExists(context, args)
                    AgentToolId.DIRECTORY_INFO -> directoryInfo(context, args)
                    AgentToolId.COMPARE_FILES -> compareFiles(context, args)
                    AgentToolId.DETECT_PROJECT_TYPE -> detectProjectType(context)
                    AgentToolId.GET_OS_INFO -> osInfo()
                    AgentToolId.GET_RUNTIME_INFO -> runtimeInfo()
                    AgentToolId.GET_STORAGE_INFO -> storageInfo()
                    AgentToolId.GET_GIT_BRANCH -> gitBranch(context)
                    AgentToolId.GET_GIT_REMOTES -> gitRemotes(context)
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
                    AgentToolId.ANDROID_BUILD_APK, AgentToolId.ANDROID_BUILD_AAB, AgentToolId.GITHUB_DISPATCH_WORKFLOW -> dispatch(context, args, id)
                    AgentToolId.CLEAN_PROJECT -> cleanProject(context)
                    AgentToolId.INSPECT_BUILD_ERROR -> inspectError(args)
                    AgentToolId.GET_BUILD_OUTPUT, AgentToolId.GITHUB_GET_WORKFLOW_LOGS -> workflowLogs(context, args)
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
                    AgentToolId.GITHUB_GET_ACTIONS -> githubActions(context, args)
                    AgentToolId.GITHUB_GET_ARTIFACT -> artifacts(context, args)
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

    private fun description(id: AgentToolId): String = when (id) {
        AgentToolId.GITHUB_GET_ACTIONS ->
            "List recent GitHub Actions runs for the active GitHub workspace. owner, repository and workflowFile are optional; DevForge fills them from the selected workspace."
        AgentToolId.GITHUB_GET_WORKFLOW_LOGS, AgentToolId.GET_BUILD_OUTPUT ->
            "Read current GitHub Actions job logs for the active or latest workflow run. owner, repository and runId are optional; DevForge infers the selected GitHub workspace and latest run when omitted. Use this while a run is in progress to inspect live logs before deciding the next action."
        AgentToolId.GITHUB_DISPATCH_WORKFLOW, AgentToolId.BUILD_PROJECT, AgentToolId.RUN_TESTS, AgentToolId.RUN_TEST, AgentToolId.RUN_LINT,
        AgentToolId.ANDROID_BUILD_APK, AgentToolId.ANDROID_BUILD_AAB ->
            "Dispatch the configured GitHub Actions build workflow for the active GitHub workspace. owner, repository, workflowFile and branch are optional; DevForge fills missing values from the selected workspace."
        AgentToolId.GITHUB_GET_ARTIFACT ->
            "List artifacts for a GitHub Actions run. owner, repository and runId are optional; DevForge infers the active workspace and latest run when omitted."
        else -> id.wireName.replace('_',' ') + " operational tool."
    }
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


    private suspend fun fileExists(context: AgentToolContext,args: JSONObject): AgentToolResult {
        val path=args.optString("path").trim(); require(path.isNotBlank())
        val uri=runCatching{resolveRoot(context.workspaceId,path)}.getOrNull(); val meta=uri?.let(::documentMetadata)
        val out=JSONObject().put("path",path).put("exists",uri!=null)
        meta?.name?.let{out.put("name",it)}; meta?.mimeType?.let{out.put("mimeType",it)}; meta?.sizeBytes?.let{out.put("sizeBytes",it)}
        return AgentToolResult.Success(if(uri==null)"Path does not exist: $path" else "Path exists: $path",output=out.toString())
    }
    private suspend fun directoryInfo(context: AgentToolContext,args: JSONObject): AgentToolResult {
        val path=args.optString("path").trim().trim('/'); val uri=resolveRoot(context.workspaceId,path); val meta=documentMetadata(uri)
        require(meta.mimeType==DocumentsContract.Document.MIME_TYPE_DIR){"Path is not a directory: $path"}
        val e=tree.list(uri,500); val d=e.count{it.isDirectory}; val out=JSONObject().put("path",path).put("name",meta.name ?: path.substringAfterLast('/')).put("itemCount",e.size).put("directories",d).put("files",e.size-d).put("truncated",e.size>=500)
        return AgentToolResult.Success("Inspected directory "+path.ifBlank{"(workspace root)"}+".",output=out.toString())
    }
    private suspend fun compareFiles(context: AgentToolContext,args: JSONObject): AgentToolResult {
        val leftPath=args.optString("leftPath").trim(); val rightPath=args.optString("rightPath").trim(); require(leftPath.isNotBlank()&&rightPath.isNotBlank())
        val left=readTextBounded(context.workspaceId,leftPath,512*1024); val right=readTextBounded(context.workspaceId,rightPath,512*1024)
        val a=left.split('
'); val b=right.split('
'); val max=maxOf(a.size,b.size); var line=-1; var av=""; var bv=""
        for(i in 0 until max){val x=a.getOrNull(i); val y=b.getOrNull(i); if(x!=y){line=i+1;av=x?:"<missing>";bv=y?:"<missing>";break}}
        val out=JSONObject().put("leftPath",leftPath).put("rightPath",rightPath).put("equal",line<0).put("firstDifferentLine",line).put("left",av.take(240)).put("right",bv.take(240))
        return AgentToolResult.Success(if(line<0)"Files are identical." else "Files differ first at line $line.",output=out.toString())
    }
    private suspend fun detectProjectType(context: AgentToolContext): AgentToolResult {
        val e=tree.list(root(context.workspaceId),500); val n=e.map{it.name}.toSet(); val t=linkedSetOf<String>()
        if("AndroidManifest.xml" in n)t+="Android"; if("build.gradle" in n||"build.gradle.kts" in n||"settings.gradle" in n||"settings.gradle.kts" in n)t+="Gradle"
        if("package.json" in n)t+="Node.js"; if("pyproject.toml" in n||"requirements.txt" in n||"setup.py" in n)t+="Python"; if("Cargo.toml" in n)t+="Rust"; if("go.mod" in n)t+="Go"
        if(e.any{!it.isDirectory&&it.name.endsWith(".kt",true)})t+="Kotlin"; if(e.any{!it.isDirectory&&it.name.endsWith(".java",true)})t+="Java"; if(t.isEmpty())t+="Unknown"
        return AgentToolResult.Success("Detected project type(s): "+t.joinToString(", ")+".",output=JSONObject().put("types",JSONArray(t.toList())).put("rootEntries",JSONArray(e.take(120).map{it.name})).toString())
    }
    private fun osInfo(): AgentToolResult = AgentToolResult.Success("Read Android/Linux OS information.",output=JSONObject().put("androidSdk",Build.VERSION.SDK_INT).put("androidRelease",Build.VERSION.RELEASE).put("manufacturer",Build.MANUFACTURER).put("model",Build.MODEL).put("device",Build.DEVICE).put("kernel",System.getProperty("os.version")?:"unknown").put("arch",System.getProperty("os.arch")?:"unknown").toString())
    private fun runtimeInfo(): AgentToolResult {
        val r=Runtime.getRuntime(); val m=ActivityManager.MemoryInfo(); (app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(m)
        return AgentToolResult.Success("Read bounded runtime information.",output=JSONObject().put("jvmMaxMemoryBytes",r.maxMemory()).put("jvmTotalMemoryBytes",r.totalMemory()).put("jvmFreeMemoryBytes",r.freeMemory()).put("deviceAvailableMemoryBytes",m.availMem).put("deviceTotalMemoryBytes",m.totalMem).put("deviceLowMemory",m.lowMemory).put("deviceMemoryThresholdBytes",m.threshold).toString())
    }
    private fun storageInfo(): AgentToolResult {
        val s=StatFs(app.filesDir.path); val b=s.blockSizeLong
        return AgentToolResult.Success("Read DevForge storage information.",output=JSONObject().put("path","DevForge app storage").put("totalBytes",s.blockCountLong*b).put("availableBytes",s.availableBlocksLong*b).put("freeBytes",s.freeBlocksLong*b).toString())
    }
    private suspend fun gitBranch(context: AgentToolContext): AgentToolResult {
        val d=gitRepo.detect(root(context.workspaceId)); if(d !is GitDetectionState.Detected)return AgentToolResult.Failure("No local Git repository detected."); val r=d.repository
        return AgentToolResult.Success("Git branch information read.",output=JSONObject().put("branch",r.branchName?:JSONObject.NULL).put("detachedHead",r.detachedHead).put("headRevision",r.headRevision?:JSONObject.NULL).put("branchCount",r.branches.size).put("branches",JSONArray(r.branches.take(50).map{it.name})).toString())
    }
    private suspend fun gitRemotes(context: AgentToolContext): AgentToolResult {
        val gh=githubStore.get(context.workspaceId); val d=gitRepo.detect(root(context.workspaceId)); val local=d is GitDetectionState.Detected && !d.repository.remoteUrl.isNullOrBlank()
        val out=JSONObject().put("localRemoteConfigured",local).put("githubLinked",gh!=null); gh?.let{out.put("githubRepository",it.owner+"/"+it.repository).put("githubBranch",it.branch)}
        return AgentToolResult.Success("Read Git remote configuration without exposing credentials.",output=out.toString())
    }
    private data class DocumentMetadata(val name:String?,val mimeType:String?,val sizeBytes:Long?,val modifiedAtEpochMs:Long?)
    private fun documentMetadata(uri:Uri):DocumentMetadata {
        var n:String?=null; var m:String?=null; var s:Long?=null; var lm:Long?=null
        resolver.query(uri,arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED),null,null,null)?.use{c->
            if(c.moveToFirst()){val ni=c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);val mi=c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);val si=c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE);val li=c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                if(ni>=0)n=c.getString(ni);if(mi>=0)m=c.getString(mi);if(si>=0&&!c.isNull(si))s=c.getLong(si);if(li>=0&&!c.isNull(li))lm=c.getLong(li)}
        }; return DocumentMetadata(n,m,s,lm)
    }
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
    private suspend fun readTextBounded(workspaceId: String,path:String,maxBytes:Int):String{
        val uri=resolveRoot(workspaceId,path)
        return runInterruptible(Dispatchers.IO){resolver.openInputStream(uri)?.use{input->
            val out=java.io.ByteArrayOutputStream(); val buffer=ByteArray(16*1024); var total=0
            while(true){val read=input.read(buffer);if(read<0)break;total+=read;require(total<=maxBytes){"File exceeds the bounded read limit: $path"};out.write(buffer,0,read)}
            out.toByteArray().toString(Charsets.UTF_8)
        }?:error("Unable to read "+path)}
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
    private suspend fun dispatch(context:AgentToolContext,args:JSONObject,id:AgentToolId):AgentToolResult{
        val remote = githubTarget(context, args)
        val target=when(id){AgentToolId.ANDROID_BUILD_APK->BuildTarget.DebugApk;AgentToolId.ANDROID_BUILD_AAB->BuildTarget.ReleaseBundle;AgentToolId.RUN_TESTS,AgentToolId.RUN_TEST->BuildTarget.DebugApk;else->when(args.optString("target","debug_apk")){"release_apk"->BuildTarget.ReleaseApk;"release_aab"->BuildTarget.ReleaseBundle;else->BuildTarget.DebugApk}}
        val config=BuildConfiguration(
            githubOwner=remote.owner,
            githubRepository=remote.repository,
            workflowFile=args.optString("workflowFile",".github/workflows/android.yml"),
            branch=args.optString("branch",remote.branch),
            buildTask=args.optString("buildTask",target.buildTask),
            artifactName=target.artifactName,
            target=target,
        )
        return when(val r=actionsGateway.dispatch(remote.owner,remote.repository,config)){
            is com.mrredhood.devforge.core.github.GitHubDispatchResult.Started->
                AgentToolResult.Success("Dispatched "+target.label+" workflow.",output=JSONObject().put("runId",r.runId).put("url",r.htmlUrl ?: JSONObject.NULL).put("repository",remote.owner+"/"+remote.repository).toString())
            is com.mrredhood.devforge.core.github.GitHubDispatchResult.Failure->AgentToolResult.Failure(r.message)
        }
    }
    private suspend fun cleanProject(context:AgentToolContext):AgentToolResult{val root=root(context.workspaceId);val builds=mutableListOf<Uri>();suspend fun visit(uri:Uri,depth:Int){if(depth>10||builds.size>=20)return;tree.list(uri,500).forEach{e->if(e.name==".git")return@forEach;if(e.isDirectory&&e.name=="build")builds+=e.uri else if(e.isDirectory)visit(e.uri,depth+1)}};visit(root,0);builds.forEach{deleteRecursive(it)};return AgentToolResult.Success("Removed "+builds.size+" build directory(s).")}
    private fun inspectError(args:JSONObject):AgentToolResult{val text=args.optString("text");require(text.isNotBlank());val hits=text.lines().filter{Regex("(?i)(error:|failure:|failed|unresolved reference|assertionerror|exception)").containsMatchIn(it)}.take(160);return AgentToolResult.Success("Extracted likely failures.",output=hits.joinToString("\n"))}
    private suspend fun workflowLogs(context:AgentToolContext,args:JSONObject):AgentToolResult{
        val remote = githubTarget(context, args)
        val runId = resolveRunId(remote,args)
        if(runId <= 0L) return AgentToolResult.Failure("No GitHub Actions run is available for the active workspace.")
        val run = actionsGateway.getRun(remote.owner,remote.repository,runId)
        val runMetadata = when(run){
            is com.mrredhood.devforge.core.github.GitHubRunResult.Success -> JSONObject()
                .put("runId",run.run.id)
                .put("runNumber",run.run.runNumber)
                .put("status",run.run.status)
                .put("conclusion",run.run.conclusion ?: JSONObject.NULL)
                .put("branch",run.run.branch)
                .put("url",run.run.htmlUrl ?: JSONObject.NULL)
            is com.mrredhood.devforge.core.github.GitHubRunResult.Failure -> JSONObject()
                .put("runId",runId)
                .put("status","unknown")
                .put("runLookupError",run.message)
        }
        return when(val r=actionsGateway.fetchLogs(remote.owner,remote.repository,runId)){
            is GitHubLogsResult.Success->AgentToolResult.Success(
                "Fetched live/current logs for run "+runId+".",
                output=JSONObject()
                    .put("repository",remote.owner+"/"+remote.repository)
                    .put("run",runMetadata)
                    .put("truncated",r.truncated)
                    .put("jobs",JSONArray(r.jobs.map{job->
                        JSONObject()
                            .put("jobId",job.jobId)
                            .put("jobName",job.jobName)
                            .put("status",job.status)
                            .put("conclusion",job.conclusion ?: JSONObject.NULL)
                            .put("url",job.htmlUrl ?: JSONObject.NULL)
                            .put("log",job.text)
                    }))
                    .toString()
                    .take(100_000),
            )
            is GitHubLogsResult.Failure->AgentToolResult.Failure(r.message)
        }
    }
    private suspend fun startProcess(context:AgentToolContext,args:JSONObject):AgentToolResult{val command=args.optString("command").trim();require(command.isNotBlank());val dir=args.optString("workingDirectory").trim().trim('/');val timeout=args.optLong("timeoutMs",TerminalCommandPolicy.DEFAULT_TIMEOUT_MS).coerceIn(250,TerminalCommandPolicy.MAX_TIMEOUT_MS);val parsed=TerminalCommandParser.parseToolCommand(command,dir,timeout,context.taskId+"-"+UUID.randomUUID());val id="proc-"+UUID.randomUUID().toString().take(10);val out=StringBuilder();val job=processScope.launch{val r=terminal.executeAuthorizedStreaming(context.workspaceId,parsed){chunk->synchronized(out){if(out.length<TerminalCommandPolicy.MAX_OUTPUT_BYTES)out.append(chunk.take(TerminalCommandPolicy.MAX_OUTPUT_BYTES-out.length))}};processes[id]?.state=when(r){is TerminalCapabilityResult.Completed->r.execution.status.name.lowercase();is TerminalCapabilityResult.Failure->"failed";is TerminalCapabilityResult.ApprovalRequired->"approval_required"}};processes[id]=ManagedProcess(job,out,"running",command);return AgentToolResult.Success("Started "+id+".",output=JSONObject().put("processId",id).toString())}
    private fun processStatus(args:JSONObject):AgentToolResult{val p=processes[args.optString("processId")]?:return AgentToolResult.Failure("Process not found.");return AgentToolResult.Success("Process status.",output=JSONObject().put("state",p.state).put("active",p.job.isActive).put("command",p.command).toString())}
    private fun stopProcess(args:JSONObject):AgentToolResult{val id=args.optString("processId");val p=processes[id]?:return AgentToolResult.Failure("Process not found.");p.job.cancel(CancellationException("Stopped"));p.state="cancelled";return AgentToolResult.Success("Stopped "+id+".")}
    private fun listProcesses():AgentToolResult=AgentToolResult.Success("Listed processes.",output=JSONArray(processes.map{(id,p)->JSONObject().put("id",id).put("state",p.state).put("active",p.job.isActive).put("command",p.command)}).toString())
    private fun processOutput(args:JSONObject):AgentToolResult{val p=processes[args.optString("processId")]?:return AgentToolResult.Failure("Process not found.");return AgentToolResult.Success("Process output.",output=synchronized(p.output){p.output.toString()}.take(60_000))}
    private suspend fun gitStatus(context:AgentToolContext):AgentToolResult{val root=root(context.workspaceId);val d=gitRepo.detect(root);if(d !is GitDetectionState.Detected)return AgentToolResult.Success("No Git repository detected.");val s=gitStatus.inspect(root,d.repository.gitDirectoryUri,d.repository.headRevision,300);return AgentToolResult.Success(s.message ?: "Git status inspected.",output=JSONObject().put("mode",s.mode.name).put("truncated",s.truncated).put("files",JSONArray(s.files.map{JSONObject().put("path",it.path).put("status",it.gitStatus.name)})).toString())}
    private suspend fun gitDiff(context:AgentToolContext,args:JSONObject):AgentToolResult {
        val root = root(context.workspaceId)
        val detected = gitRepo.detect(root)
        if (detected !is GitDetectionState.Detected) return AgentToolResult.Success("No Git repository detected.")
        val docs = gitDiff.compute(
            root,
            detected.repository.gitDirectoryUri,
            detected.repository.headRevision,
            args.optInt("maxFiles", 40).coerceIn(1, 80),
        )
        val output = docs.joinToString("\n\n") { doc ->
            val sections = doc.sections.joinToString("\n") { section ->
                section.title + "\n" + section.lines.joinToString("\n") { it.toString() }
            }
            "## " + doc.path + " (" + doc.status.name + ")\n" + sections
        }
        return AgentToolResult.Success("Computed Git diff.", output = output.take(100_000))
    }

    private fun filterDiff(result:AgentToolResult,path:String):AgentToolResult{val text=(result as? AgentToolResult.Success)?.output.orEmpty();return AgentToolResult.Success("Filtered Git diff.",output=text.split("\n\n").filter{it.contains("## "+path+" ")}.joinToString("\n\n"))}
    private suspend fun gitSync(context:AgentToolContext,id:AgentToolId,args:JSONObject):AgentToolResult{val d=gitRepo.detect(root(context.workspaceId));if(d !is GitDetectionState.Detected)return AgentToolResult.Failure("No local Git repository detected.");return when(val r=gitRemote.autoSyncChanges(d.repository,args.optString("message","DevForge AI "+id.wireName).take(160))){is GitRemoteResult.Success->AgentToolResult.Success(r.message);is GitRemoteResult.Failure->AgentToolResult.Failure(r.message)}}
    private suspend fun githubTarget(context:AgentToolContext,args:JSONObject):com.mrredhood.devforge.core.workspace.GitHubWorkspaceRemote{
        val stored = githubStore.get(context.workspaceId)
        val owner = args.optString("owner").trim().ifBlank { stored?.owner.orEmpty() }
        val repository = args.optString("repository").trim().ifBlank { stored?.repository.orEmpty() }
        require(owner.isNotBlank()) { "GitHub owner is required or the active workspace must be linked to GitHub." }
        require(repository.isNotBlank()) { "GitHub repository is required or the active workspace must be linked to GitHub." }
        return stored?.copy(
            owner = owner,
            repository = repository,
            branch = args.optString("branch").trim().ifBlank { stored.branch },
        ) ?: com.mrredhood.devforge.core.workspace.GitHubWorkspaceRemote(
            workspaceId = context.workspaceId,
            owner = owner,
            repository = repository,
            branch = args.optString("branch","main").ifBlank { "main" },
        )
    }

    private suspend fun resolveRunId(remote:com.mrredhood.devforge.core.workspace.GitHubWorkspaceRemote,args:JSONObject):Long{
        val explicit = args.optLong("runId",0L)
        if(explicit > 0L) return explicit
        return when(val runs=actionsGateway.listRecentRuns(
            remote.owner,
            remote.repository,
            args.optString("workflowFile").takeIf{it.isNotBlank()},
            20,
        )){
            is GitHubWorkflowRunsResult.Success -> runs.runs
                .sortedWith(compareByDescending<com.mrredhood.devforge.core.github.GitHubWorkflowRun>{ it.branch == remote.branch }.thenByDescending { it.runNumber })
                .firstOrNull()?.id ?: 0L
            is GitHubWorkflowRunsResult.Failure -> 0L
        }
    }

    private suspend fun remote(context:AgentToolContext)=githubStore.get(context.workspaceId)
    private suspend fun createBranch(context:AgentToolContext,args:JSONObject):AgentToolResult{val r=remote(context)?:return AgentToolResult.Failure("Workspace is not linked to GitHub.");return github.createBranch(r.owner,r.repository,args.optString("branch"),args.optString("fromBranch",r.branch)).fold({AgentToolResult.Success("Created branch "+it)},{AgentToolResult.Failure(it.message?:"Unable to create branch.")})}
    private suspend fun createPr(context:AgentToolContext,args:JSONObject):AgentToolResult{val r=remote(context)?:return AgentToolResult.Failure("Workspace is not linked to GitHub.");return github.createPullRequest(r.owner,r.repository,args.optString("title"),args.optString("head",r.branch),args.optString("base","main"),args.optString("body")).fold({AgentToolResult.Success("Created pull request.",output=it)},{AgentToolResult.Failure(it.message?:"Unable to create PR.")})}
    private suspend fun createIssue(context:AgentToolContext,args:JSONObject):AgentToolResult{val r=remote(context)?:return AgentToolResult.Failure("Workspace is not linked to GitHub.");return github.createIssue(r.owner,r.repository,args.optString("title"),args.optString("body")).fold({AgentToolResult.Success("Created issue.",output=it)},{AgentToolResult.Failure(it.message?:"Unable to create issue.")})}
    private suspend fun commentIssue(context:AgentToolContext,args:JSONObject):AgentToolResult{val r=remote(context)?:return AgentToolResult.Failure("Workspace is not linked to GitHub.");return github.commentIssue(r.owner,r.repository,args.optLong("issueNumber"),args.optString("body")).fold({AgentToolResult.Success("Commented on issue.",output=it)},{AgentToolResult.Failure(it.message?:"Unable to comment.")})}
    private suspend fun listIssues(context:AgentToolContext):AgentToolResult{val r=remote(context)?:return AgentToolResult.Failure("Workspace is not linked to GitHub.");return when(val x=github.listOpenIssues(r.owner,r.repository)){is com.mrredhood.devforge.core.github.GitHubActivityResult.Success->AgentToolResult.Success("Listed issues.",output=x.items.toString());is com.mrredhood.devforge.core.github.GitHubActivityResult.Failure->AgentToolResult.Failure(x.message)}}
    private suspend fun githubActions(context:AgentToolContext,args:JSONObject):AgentToolResult{
        val remote = githubTarget(context, args)
        return when(val x=actionsGateway.listRecentRuns(
            remote.owner,
            remote.repository,
            args.optString("workflowFile").takeIf{it.isNotBlank()},
            args.optInt("perPage",20),
        )){
            is GitHubWorkflowRunsResult.Success->AgentToolResult.Success(
                "Listed workflow runs.",
                output=JSONObject()
                    .put("repository",remote.owner+"/"+remote.repository)
                    .put("branch",remote.branch)
                    .put("runs",JSONArray(x.runs.map{
                        JSONObject()
                            .put("runId",it.id)
                            .put("runNumber",it.runNumber)
                            .put("name",it.name)
                            .put("status",it.status)
                            .put("conclusion",it.conclusion ?: JSONObject.NULL)
                            .put("branch",it.branch)
                            .put("url",it.htmlUrl ?: JSONObject.NULL)
                    }))
                    .toString(),
            )
            is GitHubWorkflowRunsResult.Failure->AgentToolResult.Failure(x.message)
        }
    }
    private suspend fun artifacts(context:AgentToolContext,args:JSONObject):AgentToolResult{
        val remote = githubTarget(context, args)
        val runId = resolveRunId(remote,args)
        if(runId <= 0L) return AgentToolResult.Failure("No GitHub Actions run is available for the active workspace.")
        return when(val x=actionsGateway.listArtifacts(remote.owner,remote.repository,runId)){
            is GitHubArtifactsResult.Success->AgentToolResult.Success(
                "Listed artifacts for run "+runId+".",
                output=x.artifacts.joinToString("\n"){it.name+" "+it.sizeBytes},
            )
            is GitHubArtifactsResult.Failure->AgentToolResult.Failure(x.message)
        }
    }
    private fun logcat(args:JSONObject):AgentToolResult{val p=ProcessBuilder("logcat","-d","-t",args.optInt("lines",200).coerceIn(1,1000).toString()).redirectErrorStream(true).start();val text=p.inputStream.bufferedReader().use{it.readText().take(100_000)};p.waitFor();return AgentToolResult.Success("Read Android logcat.",output=text)}
    private fun deviceInfo():AgentToolResult=AgentToolResult.Success("Device information.",output=JSONObject().put("manufacturer",Build.MANUFACTURER).put("model",Build.MODEL).put("device",Build.DEVICE).put("sdkInt",Build.VERSION.SDK_INT).put("release",Build.VERSION.RELEASE).toString())
    private fun documentName(uri:Uri)=runCatching{resolver.query(uri,arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else null}}.getOrNull()
    private fun deleteRecursive(uri:Uri){tree.list(uri,500).forEach{if(it.isDirectory)deleteRecursive(it.uri)else runCatching{DocumentsContract.deleteDocument(resolver,it.uri)}};runCatching{DocumentsContract.deleteDocument(resolver,uri)}}

    private suspend fun createCheckpoint(context:AgentToolContext):AgentToolResult{
        checkpointRoot.mkdirs()
        val dir = File(checkpointRoot, context.workspaceId).apply { mkdirs() }
        val id = "checkpoint-" + System.currentTimeMillis()
        val rootUri = root(context.workspaceId)
        val snapshot = JSONObject()
            .put("workspaceId", context.workspaceId)
            .put("createdAt", System.currentTimeMillis())
        val filesJson = JSONObject()
        var count = 0
        var totalBytes = 0

        suspend fun visit(uri: Uri, prefix: String, depth: Int) {
            if (depth > 12 || count >= 40 || totalBytes >= 8 * 1024 * 1024) return
            currentCoroutineContext().ensureActive()
            tree.list(uri, 500).forEach { entry ->
                if (entry.name == ".git") return@forEach
                val path = if (prefix.isBlank()) entry.name else prefix + "/" + entry.name
                if (entry.isDirectory) {
                    visit(entry.uri, path, depth + 1)
                } else if (count < 40) {
                    val bytes = runCatching {
                        runInterruptible(Dispatchers.IO) {
                            resolver.openInputStream(entry.uri)?.use { it.readBytes() }
                        }
                    }.getOrNull() ?: return@forEach
                    if (bytes.size <= 256 * 1024 && totalBytes + bytes.size <= 8 * 1024 * 1024) {
                        filesJson.put(path, bytes.toString(Charsets.UTF_8))
                        totalBytes += bytes.size
                        count++
                    }
                }
            }
        }
        visit(rootUri, "", 0)
        snapshot.put("files", filesJson)
        File(dir, id + ".json").writeText(snapshot.toString(), Charsets.UTF_8)
        return AgentToolResult.Success(
            "Created checkpoint " + id + " with " + count + " file(s).",
            output = JSONObject().put("checkpointId", id).put("files", count).toString(),
        )
    }

    private suspend fun compareCheckpoint(context:AgentToolContext,args:JSONObject):AgentToolResult {
        val id = args.optString("checkpointId").trim()
        val dir = File(checkpointRoot, context.workspaceId)
        val file = if (id.isBlank()) dir.listFiles()?.maxByOrNull { it.lastModified() } else File(dir, id + ".json")
        if (file == null || !file.exists()) return AgentToolResult.Failure("No checkpoint found.")
        val snapshot = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrElse {
            return AgentToolResult.Failure("Checkpoint data is invalid.")
        }
        val filesJson = snapshot.optJSONObject("files") ?: JSONObject()
        val changed = mutableListOf<String>()
        filesJson.keys().forEach { path ->
            currentCoroutineContext().ensureActive()
            val expected = filesJson.optString(path)
            val actual = runCatching { readText(context.workspaceId, path) }.getOrNull()
            if (actual != expected) changed += path
        }
        return AgentToolResult.Success("Compared workspace to checkpoint.", output = changed.joinToString("\n"))
    }

    private suspend fun restoreCheckpoint(context:AgentToolContext,args:JSONObject):AgentToolResult {
        val id = args.optString("checkpointId").trim()
        val dir = File(checkpointRoot, context.workspaceId)
        val file = if (id.isBlank()) dir.listFiles()?.maxByOrNull { it.lastModified() } else File(dir, id + ".json")
        if (file == null || !file.exists()) return AgentToolResult.Failure("No checkpoint found.")
        val snapshot = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrElse {
            return AgentToolResult.Failure("Checkpoint data is invalid.")
        }
        val filesJson = snapshot.optJSONObject("files") ?: JSONObject()
        var restored = 0
        filesJson.keys().forEach { path ->
            currentCoroutineContext().ensureActive()
            val content = filesJson.optString(path)
            val existing = runCatching { resolveRoot(context.workspaceId, path) }.getOrNull()
            if (existing == null) {
                val parentPath = path.substringBeforeLast('/', "")
                val name = path.substringAfterLast('/')
                val parent = resolveRoot(context.workspaceId, parentPath)
                val created = files.createFile(parent, name)
                runInterruptible(Dispatchers.IO) {
                    resolver.openOutputStream(created, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                }
            } else {
                writeText(context.workspaceId, path, content)
            }
            restored++
        }
        return AgentToolResult.Success("Restored " + restored + " file(s) from checkpoint.", affectedPaths = filesJson.keys().asSequence().toList())
    }

}

private data class ManagedProcess(val job:Job,val output:StringBuilder,@Volatile var state:String,val command:String)
