CREATE TABLE IF NOT EXISTS packages (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  publisher_id TEXT NOT NULL,
  publisher_name TEXT NOT NULL,
  type TEXT NOT NULL,
  api_version TEXT NOT NULL,
  minimum_devforge_version TEXT NOT NULL,
  source_page_url TEXT,
  icon_url TEXT,
  installable INTEGER NOT NULL DEFAULT 1,
  downloads INTEGER NOT NULL DEFAULT 0,
  rating REAL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS releases (
  package_id TEXT NOT NULL,
  version TEXT NOT NULL,
  object_key TEXT NOT NULL,
  size_bytes INTEGER NOT NULL,
  sha256 TEXT,
  permissions_json TEXT NOT NULL DEFAULT '[]',
  release_notes TEXT NOT NULL DEFAULT '',
  published_at TEXT NOT NULL,
  PRIMARY KEY (package_id, version),
  FOREIGN KEY (package_id) REFERENCES packages(id)
);

CREATE INDEX IF NOT EXISTS idx_packages_type ON packages(type);
CREATE INDEX IF NOT EXISTS idx_packages_updated ON packages(updated_at);
CREATE INDEX IF NOT EXISTS idx_releases_package ON releases(package_id, published_at DESC);
