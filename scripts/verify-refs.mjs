#!/usr/bin/env node
// 校验 docs/ 里的 owner/repo@branch:path 引用是否存在。
// 判定顺序：jsdelivr 文件树（权威）→ 超大仓库（>50MB，树 403）退回单文件 GET。
// 支持：目录路径（以 / 结尾）、{a,b} 花括号、省略路径（.../）按唯一文件名匹配。
import { readdir, readFile } from "node:fs/promises";
import { join } from "node:path";

const ROOTS = ["docs/feature-refs", "docs"];
const FILES = [];
for (const root of ROOTS) {
  try { for (const f of await readdir(root)) if (f.endsWith(".md")) FILES.push(join(root, f)); } catch {}
}

const RE = /([A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+)@([A-Za-z0-9_.\/-]+):([A-Za-z0-9_.\/\-{},\*… ]+)/g;
const refs = new Map();
for (const file of FILES) {
  const text = await readFile(file, "utf8");
  text.split("\n").forEach((line, i) => {
    RE.lastIndex = 0;
    let m;
    while ((m = RE.exec(line))) {
      const [, repo, branch, raw] = m;
      if (repo === "owner/repo") continue;
      const path = raw.trim().split(/\s+/)[0].replace(/[.,;)]+$/, "");
      if (!path) continue;
      const key = `${repo}@${branch}:${path}`;
      if (!refs.has(key)) refs.set(key, `${file}:${i + 1}`);
    }
  });
}
console.log(`扫描 ${FILES.length} 个文件，发现 ${refs.size} 条引用`);

const treeCache = new Map();
function treeOf(repo, branch) {
  const key = `${repo}@${branch}`;
  if (!treeCache.has(key)) {
    treeCache.set(key, (async () => {
      try {
        const r = await fetch(`https://data.jsdelivr.com/v1/package/gh/${repo}@${branch}/flat`, { signal: AbortSignal.timeout(25000) });
        const j = await r.json();
        if (Array.isArray(j.files)) return new Set(j.files.map((f) => f.name));
        if (String(j.message ?? "").includes("50 MB")) return "TOO_BIG";
      } catch {}
      return null;
    })());
  }
  return treeCache.get(key);
}

function expandBraces(path) {
  const m = path.match(/\{([^{}]+)\}/);
  if (!m) return [path];
  return m[1].split(",").flatMap((part) => expandBraces(path.replace(m[0], part)));
}

async function getOk(repo, branch, path) {
  const url = `https://gcore.jsdelivr.net/gh/${repo}@${branch}/${path.replace(/^\/+/, "")}`;
  for (let i = 0; i < 3; i++) {
    try {
      const r = await fetch(url, { headers: { Range: "bytes=0-64" }, signal: AbortSignal.timeout(25000) });
      if (r.status === 200 || r.status === 206 || r.status === 416) return "OK";
      if (r.status === 404) return "MISSING";
    } catch {}
  }
  return "ERR";
}

const results = [];
for (const [ref, where] of refs) {
  const idx = ref.indexOf(":");
  const repoBranch = ref.slice(0, idx);
  const path = ref.slice(idx + 1);
  const at = repoBranch.lastIndexOf("@");
  const repo = repoBranch.slice(0, at);
  const branch = repoBranch.slice(at + 1);
  const tree = await treeOf(repo, branch);

  const paths = expandBraces(path);
  const statuses = [];
  for (const p of paths) {
    const isDir = p.endsWith("/");
    if (tree instanceof Set) {
      const want = "/" + p.replace(/^\/+/, "");
      if (isDir) { statuses.push([...tree].some((n) => n.startsWith(want)) ? "OK" : "MISSING"); continue; }
      if (tree.has(want)) { statuses.push("OK"); continue; }
      const base = p.split("/").filter(Boolean).pop();
      const hits = [...tree].filter((n) => n.endsWith("/" + base));
      if (hits.length === 1) { statuses.push("OK"); continue; }
      if (hits.length > 1) { statuses.push("AMBIGUOUS"); continue; }
      statuses.push("MISSING");
    } else if (tree === "TOO_BIG") {
      if (p.includes("...") || p.includes("…") || isDir) { statuses.push("UNVERIFIABLE(超大仓库)"); continue; }
      statuses.push(await getOk(repo, branch, p));
    } else {
      statuses.push("NO_TREE");
    }
  }
  const worst = statuses.find((s) => s !== "OK") ?? "OK";
  results.push({ ref, where, status: worst, all: statuses.join(",") });
}

const ok = results.filter((r) => r.status === "OK");
const bad = results.filter((r) => r.status !== "OK");
console.log(`\nOK ${ok.length} / 异常 ${bad.length}`);
for (const r of bad) console.log(`  [${r.status}] ${r.ref}  <- ${r.where}`);
process.exit(bad.length ? 1 : 0);
