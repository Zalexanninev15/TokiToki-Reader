#!/usr/bin/env python3
"""Static checks that run before Gradle sees the code.

None of these replace the compiler. They exist because the failures they catch are
cheap to make with scripted edits and expensive to discover: a full Android build takes
minutes, and a mangled import wastes all of it.

  1. duplicate imports        -> "Conflicting import: ... is ambiguous"
  2. imports never referenced -> usually a mangled or leftover line
  3. project types used without an import
  4. R.string.* referenced but missing from the default locale
  5. literal "${'$'}" left in source (a template that leaked as text)
  6. common Compose/coroutine functions used without their import — these start with a
     lowercase letter, so the checks above skip them, and a missing one is exactly what
     a scripted import insertion gets wrong

Exit code 1 on any finding.
"""
import re
import sys
import pathlib
import collections

ROOT = pathlib.Path(__file__).resolve().parent.parent
PKG = "io.github.zalexanninev15.tokitoki"

# Function imports that are easy to forget and impossible to infer from the name alone.
# Value is the package the symbol must be imported from.
FUNCTION_IMPORTS = {
    "remember": "androidx.compose.runtime",
    "rememberCoroutineScope": "androidx.compose.runtime",
    "rememberSaveable": "androidx.compose.runtime.saveable",
    "mutableStateOf": "androidx.compose.runtime",
    "mutableFloatStateOf": "androidx.compose.runtime",
    "mutableIntStateOf": "androidx.compose.runtime",
    "derivedStateOf": "androidx.compose.runtime",
    "snapshotFlow": "androidx.compose.runtime",
    "rememberLazyListState": "androidx.compose.foundation.lazy",
    "rememberPagerState": "androidx.compose.foundation.pager",
    "rememberScrollState": "androidx.compose.foundation",
    "collectAsStateWithLifecycle": "androidx.lifecycle.compose",
    "stringResource": "androidx.compose.ui.res",
    "painterResource": "androidx.compose.ui.res",
    "viewModel": "androidx.lifecycle.viewmodel.compose",
    "verticalScroll": "androidx.compose.foundation",
    "combinedClickable": "androidx.compose.foundation",
    "clickable": "androidx.compose.foundation",
    "toUri": "androidx.core.net",
}

def kotlin_sources():
    for root in ("app/src", "domain/src", "data"):
        yield from (ROOT / root).rglob("*.kt")

def main() -> int:
    problems = []

    declared, pkg_types = {}, collections.defaultdict(set)
    for path in kotlin_sources():
        text = path.read_text(encoding="utf-8")
        package = re.search(r"^package\s+([\w.]+)", text, re.M)
        if not package:
            continue
        package = package.group(1)
        for match in re.finditer(
            r"^(?:@\w+\s+)*(?:public |internal |abstract |open |sealed |data |value |enum |annotation )*"
            r"(?:class|interface|object)\s+(\w+)", text, re.M):
            declared.setdefault(match.group(1), set()).add(f"{package}.{match.group(1)}")
            pkg_types[package].add(match.group(1))

    for path in kotlin_sources():
        text = path.read_text(encoding="utf-8")
        rel = path.relative_to(ROOT)
        package = re.search(r"^package\s+([\w.]+)", text, re.M).group(1)
        imports = re.findall(r"^import\s+([\w.]+)(?:\s+as\s+(\w+))?\s*$", text, re.M)
        body = re.sub(r"^(?:import|package) .*$", "", text, flags=re.M)
        # Comments mention type names all the time; only real code counts here.
        code = re.sub(r"/\*.*?\*/", "", body, flags=re.S)
        code = re.sub(r"//.*$", "", code, flags=re.M)

        for fq, count in collections.Counter(fq for fq, _ in imports).items():
            if count > 1:
                problems.append(f"{rel}: duplicate import {fq}")

        for fq, alias in imports:
            name = alias or fq.rsplit(".", 1)[-1]
            if name == "*" or name[0].islower():
                continue
            if not re.search(r"(?<![\w])" + re.escape(name) + r"(?![\w])", body):  # comments count as use
                problems.append(f"{rel}: import never used: {fq}")

        imported_names = {alias or fq.rsplit(".", 1)[-1] for fq, alias in imports}
        stars = {fq for fq, _ in imports if fq.endswith(".*")}
        for name, fqns in declared.items():
            if name in imported_names or name in pkg_types[package]:
                continue
            if any(f.rsplit(".", 1)[0] + ".*" in stars for f in fqns):
                continue
            if not any(f.startswith(PKG) for f in fqns):
                continue
            if re.search(r"(?<![\w.])" + re.escape(name) + r"(?![\w])", code):
                problems.append(f"{rel}: {name} used without an import")

        for symbol, package in FUNCTION_IMPORTS.items():
            if not re.search(r"(?<![\w.])" + symbol + r"\s*[({<]", code):
                continue
            expected = f"{package}.{symbol}"
            if expected in {fq for fq, _ in imports}:
                continue
            if f"{package}.*" in {fq for fq, _ in imports}:
                continue
            if package == PKG or symbol in pkg_types.get(package, ()):  # defined locally
                continue
            if re.search(r"^\s*(?:private |internal )?fun\s+" + symbol + r"\b", text, re.M):
                continue
            problems.append(f"{rel}: {symbol}() used without importing {expected}")

        if "${'$'}" in text:
            problems.append(f"{rel}: literal \"${{'$'}}\" left in source")

    strings = ROOT / "app/src/main/res/values/strings.xml"
    defined = set(re.findall(r'name="([\w_]+)"', strings.read_text(encoding="utf-8")))
    for path in (ROOT / "app/src").rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        for name in re.findall(r"R\.string\.([\w_]+)", text):
            if name not in defined:
                problems.append(f"{path.relative_to(ROOT)}: missing string resource {name}")

    if problems:
        print("Source checks failed:")
        for problem in sorted(set(problems)):
            print(f"  {problem}")
        return 1

    print("Source checks passed.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
