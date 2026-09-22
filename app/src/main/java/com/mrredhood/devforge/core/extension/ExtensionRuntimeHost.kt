package com.mrredhood.devforge.core.extension

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File

data class RuntimeCommand(
    val extensionId: String,
    val name: String,
)

class ExtensionRuntimeHost(
    private val onCommand: (RuntimeCommand) -> Unit,
    private val onStatus: (String) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var activeExtensionId: String = ""
    private val commands = linkedMapOf<String, String>()

    @SuppressLint("SetJavaScriptEnabled")
    fun attach(view: WebView) {
        webView = view
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.allowFileAccess = false
        view.settings.allowContentAccess = false
        view.settings.blockNetworkLoads = true
        view.webViewClient = WebViewClient()
        view.addJavascriptInterface(Bridge(), "DevForgeBridge")
    }

    fun activate(extension: InstalledExtension) {
        val view = webView ?: return
        if (!extension.enabled) return
        val entry = File(extension.manifest.rootPath, extension.manifest.entryPoint)
        if (!entry.isFile) {
            onStatus("Extension entry point is missing.")
            return
        }
        val source = runCatching { entry.readText(Charsets.UTF_8) }.getOrElse {
            onStatus("Unable to read extension entry point.")
            return
        }
        activeExtensionId = extension.manifest.id
        commands.keys.filter { it.startsWith(activeExtensionId + "::") }.toList().forEach { commands.remove(it) }
        val shim = when (extension.manifest.source) {
            ExtensionSource.ACODE -> acodeShim(extension.manifest.id)
            ExtensionSource.VSCODE -> vscodeShim(extension.manifest.id)
            ExtensionSource.DEVFORGE -> return
        }
        val html = "<!doctype html><html><body><script>" +
            shim +
            "</script><script>try{" +
            source.replace("</script>", "<\\/script>") +
            ";try{window.__dfActivate&&window.__dfActivate();}catch(e){DevForgeBridge.status('Activation failed: '+(e&&e.message?e.message:e));}</script></body></html>"
        view.loadDataWithBaseURL(
            "https://devforge.invalid/" + extension.manifest.id + "/",
            html,
            "text/html",
            "UTF-8",
            null,
        )
        onStatus("Activating " + extension.manifest.name + "…")
    }

    fun runCommand(extensionId: String, name: String) {
        val view = webView ?: return
        val key = extensionId + "::" + name
        if (!commands.containsKey(key)) {
            onStatus("Command '" + name + "' is not registered by the extension.")
            return
        }
        view.evaluateJavascript("window.__dfCommands[" + quote(key) + "]();", null)
    }

    fun dispose() {
        webView?.stopLoading()
        webView = null
        activeExtensionId = ""
        commands.clear()
    }

    private fun acodeShim(extensionId: String): String {
        val prefix = quote(extensionId + "::")
        val pluginId = quote(extensionId)
        return """
            window.__dfCommands={}; window.__dfInits={};
            window.acode={
              setPluginInit:function(id,fn){window.__dfInits[id]=fn;},
              setPluginUnmount:function(){},
              alert:function(m){DevForgeBridge.status(String(m));},
              toast:function(m){DevForgeBridge.status(String(m));},
              confirm:function(){return Promise.resolve(true);},
              require:function(module){
                if(module==='commands') return {
                  addCommand:function(command){
                    var name=String(command.name||'unnamed');
                    window.__dfCommands[$prefix+name]=command.exec;
                    DevForgeBridge.commandRegistered(name);
                  },
                  removeCommand:function(name){delete window.__dfCommands[$prefix+String(name)];}
                };
                if(module==='editor') return {
                  getValue:function(){return DevForgeBridge.editorGetValue();},
                  setValue:function(value){DevForgeBridge.editorSetValue(String(value));}
                };
                if(module==='settings') return {get:function(){return undefined;},set:function(){}};
                if(module==='storage') return {getItem:function(){return null;},setItem:function(){}};
                if(module==='keyboard') return {on:function(){}};
                if(module==='fileIcons') return {register:function(){return {dispose:function(){}};}};
                throw new Error('Unsupported Acode module: '+module);
              }
            };
            window.__dfActivate=function(){
              var init=window.__dfInits[$pluginId];
              if(!init) throw new Error('Acode plugin did not register setPluginInit.');
              init('https://devforge.invalid/' + $pluginId + '/', {innerHTML:'',show:function(){}}, {});
              DevForgeBridge.status('Acode plugin activated.');
            };
        """.trimIndent()
    }

    private fun vscodeShim(extensionId: String): String {
        val prefix = quote(extensionId + "::")
        return """
            window.__dfCommands={};
            window.__dfVscode={
              commands:{
                registerCommand:function(name,callback){
                  window.__dfCommands[$prefix+String(name)]=callback;
                  DevForgeBridge.commandRegistered(String(name));
                  return {dispose:function(){}};
                }
              },
              window:{
                showInformationMessage:function(m){DevForgeBridge.status(String(m));return Promise.resolve();},
                showWarningMessage:function(m){DevForgeBridge.status(String(m));return Promise.resolve();},
                showErrorMessage:function(m){DevForgeBridge.status(String(m));return Promise.resolve();}
              },
              env:{appName:'DevForge'},
              workspace:{getConfiguration:function(){return {get:function(){return undefined;}};}},
              Uri:{file:function(path){return {fsPath:String(path),path:String(path)};}}
            };
            window.require=function(name){
              if(name==='vscode') return window.__dfVscode;
              throw new Error('Unsupported module: '+name);
            };
            window.__dfActivate=function(){DevForgeBridge.status('VS Code Web extension script loaded.');};
        """.trimIndent()
    }

    private fun quote(value: String): String = org.json.JSONObject.quote(value)

    private inner class Bridge {
        @JavascriptInterface fun commandRegistered(name: String) {
            mainHandler.post {
                if (activeExtensionId.isBlank()) return@post
                commands[activeExtensionId + "::" + name] = name
                onCommand(RuntimeCommand(activeExtensionId, name))
            }
        }

        @JavascriptInterface fun status(message: String) {
            mainHandler.post { onStatus(message.take(500)) }
        }

        @JavascriptInterface fun editorGetValue(): String = ""

        @JavascriptInterface fun editorSetValue(value: String) {
            mainHandler.post { onStatus("Extension requested an editor update (" + value.length + " chars).") }
        }
    }
}
