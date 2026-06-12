import { createHash } from "node:crypto";
import { existsSync, readFileSync, statSync } from "node:fs";
import { join, resolve } from "node:path";

const root = resolve(process.cwd());
const passes = [];
const failures = [];

const expected = {
  gradleVersion: "8.10.2",
  distributionUrl: "https://services.gradle.org/distributions/gradle-8.10.2-bin.zip",
  distributionSha256: "31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26",
  wrapperJarSha256: "2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046"
};

function pass(message) {
  passes.push(message);
}

function fail(message) {
  failures.push(message);
}

function assert(condition, message) {
  if (condition) {
    pass(message);
  } else {
    fail(message);
  }
}

function pathFor(path) {
  return join(root, path);
}

function fileExists(path) {
  return existsSync(pathFor(path));
}

function readText(path) {
  return readFileSync(pathFor(path), "utf8");
}

function sha256(path) {
  return createHash("sha256").update(readFileSync(pathFor(path))).digest("hex");
}

function parseProperties(text) {
  const props = {};
  for (const line of text.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) {
      continue;
    }
    const equals = trimmed.indexOf("=");
    if (equals <= 0) {
      continue;
    }
    props[trimmed.slice(0, equals)] = trimmed.slice(equals + 1).replace(/\\:/g, ":");
  }
  return props;
}

function checkWrapperFiles() {
  const requiredFiles = [
    "android/gradlew",
    "android/gradlew.bat",
    "android/gradle/wrapper/gradle-wrapper.jar",
    "android/gradle/wrapper/gradle-wrapper.properties"
  ];

  for (const file of requiredFiles) {
    assert(fileExists(file), `${file} exists`);
  }

  if (fileExists("android/gradle/wrapper/gradle-wrapper.jar")) {
    const size = statSync(pathFor("android/gradle/wrapper/gradle-wrapper.jar")).size;
    assert(size > 40_000, "Gradle wrapper jar has the expected non-empty size");
    assert(
      sha256("android/gradle/wrapper/gradle-wrapper.jar") === expected.wrapperJarSha256,
      `Gradle wrapper jar SHA-256 matches Gradle ${expected.gradleVersion}`
    );
  }
}

function checkWrapperProperties() {
  if (!fileExists("android/gradle/wrapper/gradle-wrapper.properties")) {
    return;
  }

  const props = parseProperties(readText("android/gradle/wrapper/gradle-wrapper.properties"));
  assert(props.distributionUrl === expected.distributionUrl, "Gradle wrapper points at the pinned Gradle distribution");
  assert(props.distributionSha256Sum === expected.distributionSha256, "Gradle wrapper verifies the Gradle distribution SHA-256");
  assert(props.validateDistributionUrl === "true", "Gradle wrapper validates the distribution URL");
  assert(props.networkTimeout === "10000", "Gradle wrapper has an explicit network timeout");
}

function checkWrapperScripts() {
  if (fileExists("android/gradlew")) {
    const sh = readText("android/gradlew");
    assert(sh.startsWith("#!/bin/sh"), "gradlew has the standard shell entrypoint");
    assert(sh.includes("org.gradle.wrapper.GradleWrapperMain"), "gradlew invokes GradleWrapperMain");
    assert(sh.includes("gradle/wrapper/gradle-wrapper.jar"), "gradlew uses the checked-in wrapper jar");
  }

  if (fileExists("android/gradlew.bat")) {
    const bat = readText("android/gradlew.bat");
    assert(bat.includes("org.gradle.wrapper.GradleWrapperMain"), "gradlew.bat invokes GradleWrapperMain");
    assert(bat.includes("gradle\\wrapper\\gradle-wrapper.jar"), "gradlew.bat uses the checked-in wrapper jar");
  }
}

function printResults() {
  for (const message of passes) {
    console.log(`PASS ${message}`);
  }
  for (const message of failures) {
    console.error(`FAIL ${message}`);
  }

  console.log(`\nAndroid wrapper source check: ${passes.length} passed, ${failures.length} failure(s).`);
  if (failures.length > 0) {
    process.exit(1);
  }
}

checkWrapperFiles();
checkWrapperProperties();
checkWrapperScripts();
printResults();
