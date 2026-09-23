export type DevForgeApiVersion = "1.0";

export interface Disposable { dispose(): void; }

export interface DevForgeApi {
  app: AppApi;
  ui: UiApi;
  workspace: WorkspaceApi;
  files: FilesApi;
  editor: EditorApi;
  languages: LanguagesApi;
  terminal: TerminalApi;
  git: GitApi;
  github: GitHubApi;
  build: BuildApi;
  artifacts: ArtifactApi;
  ai: AiApi;
  agents: AgentApi;
  tools: ToolApi;
  workflows: WorkflowApi;
  automation: AutomationApi;
  commands: CommandApi;
  settings: SettingsApi;
  storage: StorageApi;
  network: NetworkApi;
  notifications: NotificationApi;
  events: EventBus;
  capabilities: CapabilityApi;
}

export interface AppApi {
  getVersion(): Promise<string>;
  getApiVersion(): Promise<string>;
  getPlatform(): Promise<{ os: "android"; version: string; sdk: number; architecture: string }>;
  getState(): Promise<{ foreground: boolean; activeDestination?: string }>;
  open(destination: string): Promise<void>;
  openUri(uri: string): Promise<void>;
  showMessage(message: string, options?: { kind?: string; durationMs?: number }): Promise<void>;
}

export interface UiApi {
  registerPanel(options: { id: string; title: string; location: string; icon?: string }): Disposable;
  registerToolbarItem(options: { id: string; title: string; location: string; icon?: string; commandId: string }): Disposable;
  registerCommandMenuItem(options: { id: string; title: string; commandId: string; group?: string }): Disposable;
  showDialog(options: { title: string; message?: string; buttons?: string[] }): Promise<{ selectedButton?: string }>;
  showSheet(options: { title: string }): Promise<{ dismissed: boolean }>;
  showNotification(options: { title: string; message: string; kind?: string }): Disposable;
}

export interface WorkspaceApi {
  list(): Promise<Array<{ id: string; name: string; root: string }>>;
  active(): Promise<{ id: string; name: string; root: string } | null>;
  open(id: string): Promise<void>;
  close(id: string): Promise<void>;
  create(options: { name: string }): Promise<{ id: string; name: string; root: string }>;
}

export interface FilesApi {
  exists(path: string): Promise<boolean>;
  stat(path: string): Promise<{ path: string; name: string; isDirectory: boolean; sizeBytes: number; modifiedEpochMs?: number }>;
  readText(path: string): Promise<string>;
  readBytes(path: string): Promise<Uint8Array>;
  writeText(path: string, content: string): Promise<void>;
  writeBytes(path: string, content: Uint8Array): Promise<void>;
  createFile(path: string): Promise<void>;
  createDirectory(path: string): Promise<void>;
  delete(path: string): Promise<void>;
  move(from: string, to: string): Promise<void>;
  copy(from: string, to: string): Promise<void>;
  rename(path: string, name: string): Promise<void>;
  list(path?: string): Promise<Array<{ path: string; name: string; isDirectory: boolean; sizeBytes?: number }>>;
  tree(options?: { path?: string; depth?: number; limit?: number }): Promise<unknown>;
  find(options: { query: string; path?: string; limit?: number }): Promise<Array<{ uri: string; path: string; name: string }>>;
  searchText(options: { query: string; path?: string; limit?: number }): Promise<Array<{ path: string; line: number; column: number; snippet: string }>>;
}

export interface EditorApi {
  active(): Promise<unknown | null>;
  list(): Promise<unknown[]>;
  open(path: string): Promise<unknown>;
  close(id: string): Promise<void>;
  getDocument(id: string): Promise<unknown>;
  getText(id: string): Promise<string>;
  setText(id: string, text: string): Promise<void>;
  insert(id: string, position: Position, text: string): Promise<void>;
  replace(id: string, range: Range, text: string): Promise<void>;
  delete(id: string, range: Range): Promise<void>;
  applyEdits(id: string, edits: Array<{ range: Range; text: string }>): Promise<void>;
  getCursor(id: string): Promise<Position>;
  setCursor(id: string, position: Position): Promise<void>;
  getSelections(id: string): Promise<Range[]>;
  setSelections(id: string, ranges: Range[]): Promise<void>;
  save(id: string): Promise<void>;
  addDecoration(id: string, decoration: unknown): Promise<string>;
  removeDecoration(id: string, decorationId: string): Promise<void>;
  addDiagnostic(id: string, diagnostic: unknown): Promise<string>;
  removeDiagnostic(id: string, diagnosticId: string): Promise<void>;
}

export interface LanguagesApi {
  registerLanguage(definition: { id: string; label: string; extensions: string[]; aliases?: string[] }): Disposable;
  registerFormatter(provider: unknown): Disposable;
  registerLinter(provider: unknown): Disposable;
  registerCompletionProvider(provider: unknown): Disposable;
  registerHoverProvider(provider: unknown): Disposable;
  registerDefinitionProvider(provider: unknown): Disposable;
  registerReferenceProvider(provider: unknown): Disposable;
  registerCodeActionProvider(provider: unknown): Disposable;
}

export interface TerminalApi {
  listSessions(): Promise<unknown[]>;
  createSession(options?: { name?: string; workingDirectory?: string }): Promise<unknown>;
  getSession(id: string): Promise<unknown>;
  closeSession(id: string): Promise<void>;
}

export interface GitApi {
  status(): Promise<unknown>;
  diff(options?: { path?: string }): Promise<unknown>;
  log(options?: { limit?: number }): Promise<unknown[]>;
  stage(paths: string[]): Promise<void>;
  unstage(paths: string[]): Promise<void>;
  commit(options: { message: string }): Promise<unknown>;
  checkout(name: string): Promise<void>;
  fetch(): Promise<void>;
  pull(): Promise<void>;
  push(): Promise<void>;
  stash(message?: string): Promise<void>;
}

export interface GitHubApi {
  getUser(): Promise<unknown>;
  listRepositories(): Promise<unknown[]>;
  getRepository(owner: string, repository: string): Promise<unknown>;
  createRepository(options: unknown): Promise<unknown>;
  deleteRepository(owner: string, repository: string): Promise<void>;
  getFile(owner: string, repository: string, path: string, ref?: string): Promise<unknown>;
  writeFile(options: unknown): Promise<void>;
  listBranches(owner: string, repository: string): Promise<unknown[]>;
  createBranch(owner: string, repository: string, name: string, baseRef: string): Promise<unknown>;
  listIssues(owner: string, repository: string): Promise<unknown[]>;
  createIssue(options: unknown): Promise<unknown>;
  listPullRequests(owner: string, repository: string): Promise<unknown[]>;
  getPullRequest(owner: string, repository: string, number: number): Promise<unknown>;
  createPullRequest(options: unknown): Promise<unknown>;
  listWorkflowRuns(owner: string, repository: string): Promise<unknown[]>;
  dispatchWorkflow(options: unknown): Promise<unknown>;
}

export interface BuildApi {
  listConfigurations(): Promise<unknown[]>;
  getConfiguration(id: string): Promise<unknown>;
  create(options: unknown): Promise<unknown>;
  start(id: string): Promise<unknown>;
  cancel(id: string): Promise<void>;
  getStatus(id: string): Promise<unknown>;
  getLogs(id: string): Promise<unknown[]>;
  getArtifacts(id: string): Promise<unknown[]>;
}

export interface ArtifactApi {
  createFile(options: unknown): Promise<unknown>;
  createText(options: unknown): Promise<unknown>;
  createJson(options: unknown): Promise<unknown>;
  createBinary(options: unknown): Promise<unknown>;
  createDirectory(options: unknown): Promise<unknown>;
  createArchive(options: unknown): Promise<unknown>;
  createPatch(options: unknown): Promise<unknown>;
  attachToChat(artifactId: string): Promise<void>;
  get(artifactId: string): Promise<unknown>;
  delete(artifactId: string): Promise<void>;
}

export interface AiApi {
  listModels(): Promise<unknown[]>;
  listProviders(): Promise<unknown[]>;
  chat(request: unknown): Promise<unknown>;
  registerTool(definition: unknown): Disposable;
}

export interface AgentApi {
  create(definition: unknown): Promise<unknown>;
  run(agentId: string, input: string): Promise<unknown>;
  get(runId: string): Promise<unknown>;
  list(): Promise<unknown[]>;
  cancel(runId: string): Promise<void>;
  createPlan(definition: unknown): Promise<unknown>;
  executePlan(planId: string): Promise<unknown>;
}

export interface ToolApi {
  register(definition: unknown): Disposable;
  unregister(id: string): Promise<void>;
  list(): Promise<unknown[]>;
  invoke(id: string, argumentsJson: string): Promise<string>;
}

export interface WorkflowApi {
  register(definition: unknown): Disposable;
  run(id: string, inputJson?: string): Promise<unknown>;
  get(runId: string): Promise<unknown>;
  cancel(runId: string): Promise<void>;
  list(): Promise<unknown[]>;
}

export interface AutomationApi {
  register(definition: unknown): Disposable;
  enable(id: string): Promise<void>;
  disable(id: string): Promise<void>;
  run(id: string): Promise<unknown>;
  list(): Promise<unknown[]>;
}

export interface CommandApi {
  register(definition: unknown): Disposable;
  execute(id: string, argumentsJson?: string): Promise<unknown>;
  list(): Promise<unknown[]>;
}

export interface SettingsApi {
  get<T = unknown>(key: string): Promise<T | undefined>;
  set<T = unknown>(key: string, value: T): Promise<void>;
  reset(key: string): Promise<void>;
  register(schema: unknown): Disposable;
}

export interface StorageApi {
  get<T = string>(key: string): Promise<T | undefined>;
  set<T = string>(key: string, value: T): Promise<void>;
  delete(key: string): Promise<void>;
  clear(): Promise<void>;
}

export interface NetworkApi {
  fetch(request: unknown): Promise<unknown>;
}

export interface NotificationApi {
  show(options: { title: string; message: string; kind?: string }): Promise<string>;
  update(id: string, options: { title: string; message: string; kind?: string }): Promise<void>;
  dismiss(id: string): Promise<void>;
}

export interface EventBus {
  on<T = unknown>(event: { name: string; version: number }, listener: (event: T) => void): Disposable;
}

export interface CapabilityApi {
  has(name: string): Promise<boolean>;
  list(): Promise<Set<string>>;
}

export interface Position { line: number; column: number; }
export interface Range { start: Position; end: Position; }

export declare const devforge: DevForgeApi;
export declare function activate(context: { packageId: string; version: string }): Promise<void> | void;
export declare function deactivate(): Promise<void> | void;
