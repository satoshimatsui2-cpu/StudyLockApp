const fs = require("fs");
const path = require("path");

const inputFile = path.join("app", "src", "main", "res", "raw", "words.tsv");
const outputDir = path.join("app", "src", "main", "assets", "words");

if (!fs.existsSync(inputFile)) {
  console.error(`Input file not found: ${inputFile}`);
  process.exit(1);
}

fs.mkdirSync(outputDir, { recursive: true });

const rawText = fs.readFileSync(inputFile, "utf8").replace(/^\uFEFF/, "");
const lines = rawText.split(/\r?\n/).filter(line => line.trim().length > 0);

if (lines.length === 0) {
  console.error("words.tsv is empty.");
  process.exit(1);
}

const header = lines[0];
const outputLines = {};
const counters = {};

for (let grade = 1; grade <= 7; grade++) {
  const key = String(grade);
  outputLines[key] = [header];
  counters[key] = grade * 100000 + 1;
}

let total = 0;
let skipped = 0;

for (let i = 1; i < lines.length; i++) {
  const row = lines[i].split("\t");

  if (row.length < 2) {
    skipped++;
    continue;
  }

  const grade = String(row[1]).trim();

  if (!outputLines[grade]) {
    console.warn(`Skipped line ${i + 1}: unknown grade "${grade}"`);
    skipped++;
    continue;
  }

  // no を grade ごとの新ルールで振り直し
  row[0] = String(counters[grade]);
  counters[grade]++;

  outputLines[grade].push(row.join("\t"));
  total++;
}

for (let grade = 1; grade <= 7; grade++) {
  const key = String(grade);
  const filePath = path.join(outputDir, `grade${grade}.tsv`);
  fs.writeFileSync(filePath, outputLines[key].join("\n") + "\n", "utf8");

  console.log(
    `grade${grade}.tsv: ${outputLines[key].length - 1} words`
  );
}

console.log("");
console.log(`TSV splitting completed.`);
console.log(`Total written: ${total}`);
console.log(`Skipped: ${skipped}`);
console.log(`Output dir: ${outputDir}`);