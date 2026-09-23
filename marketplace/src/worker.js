const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "public, max-age=60, s-maxage=300"
};

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "access-control-allow-origin": "*",
          "access-control-allow-methods": "GET,POST,OPTIONS",
          "access-control-allow-headers": "content-type,authorization"
        }
      });
    }

    try {
      if (url.pathname === "/v1/health") {
        return json({ ok: true, apiVersion: env.MARKETPLACE_API_VERSION || "1" });
      }

      if (request.method === "GET" && url.pathname === "/v1/packages") {
        return await listPackages(url, env);
      }

      const match = url.pathname.match(/^\/v1\/packages\/([^/]+)$/);
      if (request.method === "GET" && match) {
        return await getPackage(decodeURIComponent(match[1]), env);
      }

      const download = url.pathname.match(/^\/v1\/packages\/([^/]+)\/releases\/([^/]+)\/download$/);
      if (request.method === "GET" && download) {
        return await downloadRelease(
          decodeURIComponent(download[1]),
          decodeURIComponent(download[2]),
          env
        );
      }

      if (request.method === "POST" && url.pathname === "/v1/publish") {
        return await publishPackage(request, env);
      }

      return json({ error: "NOT_FOUND", message: "Marketplace route not found." }, 404);
    } catch (error) {
      return json({
        error: "SERVER_ERROR",
        message: error && error.message ? error.message : "Marketplace request failed."
      }, 500);
    }
  }
};

async function listPackages(url, env) {
  const query = (url.searchParams.get("query") || "").trim();
  const type = (url.searchParams.get("type") || "").trim();
  const limit = Math.min(Math.max(Number(url.searchParams.get("limit") || 20), 1), 50);

  const where = [];
  const bindings = [];

  if (query) {
    where.push("(p.name LIKE ? OR p.id LIKE ? OR p.publisher_name LIKE ? OR p.description LIKE ?)");
    const like = "%" + query.replaceAll("%", "\\%").replaceAll("_", "\\_") + "%";
    bindings.push(like, like, like, like);
  }

  if (type) {
    where.push("p.type = ?");
    bindings.push(type);
  }

  const sql =
    "SELECT p.*, r.version, r.permissions_json, r.published_at, r.size_bytes, r.object_key " +
    "FROM packages p JOIN releases r ON r.package_id = p.id " +
    "AND r.published_at = (SELECT MAX(r2.published_at) FROM releases r2 WHERE r2.package_id = p.id) " +
    (where.length ? "WHERE " + where.join(" AND ") + " " : "") +
    "ORDER BY p.downloads DESC, p.updated_at DESC LIMIT ?";

  bindings.push(limit);
  const result = await env.DB.prepare(sql).bind(...bindings).all();
  return json({
    packages: (result.results || []).map(rowToPackage),
    apiVersion: env.MARKETPLACE_API_VERSION || "1"
  });
}

async function getPackage(id, env) {
  const sql =
    "SELECT p.*, r.version, r.permissions_json, r.published_at, r.size_bytes, r.object_key " +
    "FROM packages p JOIN releases r ON r.package_id = p.id " +
    "WHERE p.id = ? AND r.published_at = " +
    "(SELECT MAX(r2.published_at) FROM releases r2 WHERE r2.package_id = p.id)";
  const row = await env.DB.prepare(sql).bind(id).first();

  if (!row) {
    return json({ error: "NOT_FOUND", message: "Package not found." }, 404);
  }

  return json(rowToPackage(row));
}

async function downloadRelease(id, version, env) {
  const row = await env.DB.prepare(
    "SELECT object_key, size_bytes FROM releases WHERE package_id = ? AND version = ?"
  ).bind(id, version).first();

  if (!row) {
    return json({ error: "NOT_FOUND", message: "Package release not found." }, 404);
  }

  const object = await env.PACKAGES.get(row.object_key);
  if (!object) {
    return json({ error: "NOT_FOUND", message: "Package object not found." }, 404);
  }

  await env.DB.prepare(
    "UPDATE packages SET downloads = downloads + 1, updated_at = ? WHERE id = ?"
  ).bind(new Date().toISOString(), id).run();

  const headers = new Headers();
  headers.set("content-type", "application/octet-stream");
  headers.set(
    "content-disposition",
    "attachment; filename="" + safeFilename(id) + "-" + safeFilename(version) + ".devforge""
  );
  headers.set("cache-control", "public, max-age=31536000, immutable");
  headers.set("content-length", String(row.size_bytes));

  return new Response(object.body, { headers });
}

async function publishPackage(request, env) {
  const expected = env.PUBLISH_TOKEN;
  const authorization = request.headers.get("authorization") || "";
  if (!expected || authorization !== "Bearer " + expected) {
    return json({
      error: "UNAUTHORIZED",
      message: "Publishing requires the configured publisher token."
    }, 401);
  }

  const form = await request.formData();
  const manifestFile = form.get("manifest");
  const packageFile = form.get("package");
  const releaseNotes = String(form.get("releaseNotes") || "");

  if (!(manifestFile instanceof File) || !(packageFile instanceof File)) {
    return json({
      error: "INVALID_REQUEST",
      message: "multipart fields 'manifest' and 'package' are required."
    }, 400);
  }

  const manifest = await manifestFile.text();
  const validation = validateManifest(manifest);
  if (!validation.ok) {
    return json({ error: "INVALID_MANIFEST", message: validation.message }, 400);
  }

  const maxBytes = Number(env.MAX_PACKAGE_BYTES || 67108864);
  if (packageFile.size > maxBytes) {
    return json({
      error: "PACKAGE_TOO_LARGE",
      message: "Package exceeds the Marketplace size limit."
    }, 400);
  }

  const existingRelease = await env.DB.prepare(
    "SELECT version FROM releases WHERE package_id = ? AND version = ?"
  ).bind(validation.id, validation.version).first();

  if (existingRelease) {
    return json({
      error: "VERSION_EXISTS",
      message: "Published package versions are immutable."
    }, 409);
  }

  const objectKey = "packages/" + validation.id + "/" + validation.version + "/package.devforge";
  const now = new Date().toISOString();

  await env.PACKAGES.put(objectKey, await packageFile.arrayBuffer(), {
    httpMetadata: { contentType: "application/octet-stream" }
  });

  await env.DB.prepare(
    "INSERT INTO packages " +
    "(id,name,description,publisher_id,publisher_name,type,api_version,minimum_devforge_version,source_page_url,icon_url,installable,downloads,rating,created_at,updated_at) " +
    "VALUES (?,?,?,?,?,?,?,?,?,?,1,0,NULL,?,?) " +
    "ON CONFLICT(id) DO UPDATE SET " +
    "name=excluded.name, description=excluded.description, publisher_id=excluded.publisher_id, " +
    "publisher_name=excluded.publisher_name, type=excluded.type, api_version=excluded.api_version, " +
    "minimum_devforge_version=excluded.minimum_devforge_version, source_page_url=excluded.source_page_url, " +
    "icon_url=excluded.icon_url, updated_at=excluded.updated_at"
  ).bind(
    validation.id,
    validation.name,
    validation.description,
    validation.publisherId,
    validation.publisherName,
    validation.type,
    validation.apiVersion,
    validation.minimumVersion,
    validation.sourcePageUrl,
    validation.iconUrl,
    now,
    now
  ).run();

  await env.DB.prepare(
    "INSERT INTO releases " +
    "(package_id,version,object_key,size_bytes,sha256,permissions_json,release_notes,published_at) " +
    "VALUES (?,?,?,?,?,?,?,?)"
  ).bind(
    validation.id,
    validation.version,
    objectKey,
    packageFile.size,
    "",
    JSON.stringify(validation.permissions),
    releaseNotes,
    now
  ).run();

  return json({
    published: true,
    id: validation.id,
    version: validation.version,
    objectKey: objectKey
  }, 201);
}

function validateManifest(raw) {
  let value;
  try {
    value = JSON.parse(raw);
  } catch {
    return { ok: false, message: "manifest.json is not valid JSON." };
  }

  const required = [
    "manifestVersion",
    "id",
    "name",
    "version",
    "publisher",
    "type",
    "devforge",
    "entry",
    "permissions"
  ];

  if (required.some(function(key) { return value[key] === undefined; })) {
    return { ok: false, message: "Manifest is missing required fields." };
  }

  if (value.manifestVersion !== 1) {
    return { ok: false, message: "Unsupported manifestVersion." };
  }

  if (!/^[a-z][a-z0-9._-]{1,79}$/.test(value.id || "")) {
    return { ok: false, message: "Invalid package id." };
  }

  if (!/^[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?$/.test(value.version || "")) {
    return { ok: false, message: "Invalid package version." };
  }

  if (!/^[0-9]+\.[0-9]+$/.test(value.devforge && value.devforge.api || "")) {
    return { ok: false, message: "Invalid DevForge API version." };
  }

  if ((String(value.devforge.api).split(".")[0] || "") !== "1") {
    return { ok: false, message: "Unsupported DevForge API major version." };
  }

  if (!value.publisher.id || !value.publisher.name) {
    return { ok: false, message: "Publisher identity is required." };
  }

  if (!value.entry.main || value.entry.runtime !== "javascript") {
    return { ok: false, message: "Invalid JavaScript entry point." };
  }

  const permissions = Array.isArray(value.permissions) ? value.permissions : [];
  if (permissions.length > 32) {
    return { ok: false, message: "Too many permissions." };
  }

  return {
    ok: true,
    id: value.id,
    name: value.name,
    version: value.version,
    description: String(value.description || ""),
    publisherId: value.publisher.id,
    publisherName: value.publisher.name,
    type: value.type,
    apiVersion: value.devforge.api,
    minimumVersion: value.devforge.minimumVersion,
    sourcePageUrl: value.sourcePageUrl || null,
    iconUrl: value.iconUrl || null,
    permissions: permissions
  };
}

function rowToPackage(row) {
  let permissions = [];
  try {
    permissions = JSON.parse(row.permissions_json || "[]");
  } catch {}

  return {
    id: row.id,
    name: row.name,
    version: row.version,
    description: row.description,
    publisher: {
      id: row.publisher_id,
      name: row.publisher_name
    },
    type: row.type,
    apiVersion: row.api_version,
    minimumDevForgeVersion: row.minimum_devforge_version,
    permissions: permissions,
    sourcePageUrl: row.source_page_url,
    iconUrl: row.icon_url,
    downloads: row.downloads,
    rating: row.rating,
    installable: Boolean(row.installable),
    downloadUrl:
      "/v1/packages/" +
      encodeURIComponent(row.id) +
      "/releases/" +
      encodeURIComponent(row.version) +
      "/download",
    publishedAt: row.published_at,
    sizeBytes: row.size_bytes
  };
}

function safeFilename(value) {
  return String(value).replace(/[^A-Za-z0-9._-]/g, "_").slice(0, 80) || "package";
}

function json(value, status) {
  const headers = new Headers(JSON_HEADERS);
  headers.set("access-control-allow-origin", "*");
  return new Response(JSON.stringify(value), {
    status: status || 200,
    headers: headers
  });
}
