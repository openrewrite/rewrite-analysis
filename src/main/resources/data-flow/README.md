# Data Flow and Taint Tracking Model

`model.csv` lists methods that transfer data flow or taint; `sinks.csv` lists methods whose arguments
(or return value) are sinks. Both are generated from [CodeQL](https://codeql.github.com/)'s Java models.

Notes:
- The CSV columns are the CodeQL model columns. `ext` and `provenance` are retained in the files for
  fidelity with CodeQL but are **not** read by the loader (`ExternalFlowModels` / `ExternalSinkModels`).
- Current CodeQL writes the receiver/qualifier as `Argument[this]` (older versions used `Argument[-1]`);
  the loader understands both. The argument selector may also be a comma-separated union and/or a range,
  e.g. `Argument[this,0]` or `Argument[0..2]`.
- Content/field-sensitive paths (`Element`, `MapKey`, `MapValue`, `Field[...]`, ...) and higher-order
  paths on the non-callback side are over-approximations the engine collapses or ignores; they are kept
  in the files as-is.

## Regenerating the files

These models live in CodeQL's Java library, not in any particular project, so the queries below only
need *some* CodeQL Java database to run against — a trivial one is enough.

Prerequisites: the [`codeql` CLI](https://github.com/github/codeql-cli-binaries) and a checkout of the
[`codeql`](https://github.com/github/codeql) repository (for the `codeql/java-all` library pack).

```bash
# 1. Create a throwaway Java database (the models come from the library, not the source).
mkdir -p /tmp/qlsrc && printf 'class A {}\n' > /tmp/qlsrc/A.java
codeql database create /tmp/qldb --language=java --build-mode=none --source-root=/tmp/qlsrc --overwrite

# 2. Run a query (see below), pointing --additional-packs at your codeql checkout.
codeql query run <query>.ql --database=/tmp/qldb \
  --additional-packs=/path/to/codeql --output=/tmp/out.bqrs

# 3. Decode to CSV, then sort the data rows (keep the header first) for stable diffs, and replace the file.
codeql bqrs decode --format=csv /tmp/out.bqrs --output=/tmp/out.csv
{ head -1 /tmp/out.csv; tail -n +2 /tmp/out.csv | LC_ALL=C sort; } > src/main/resources/data-flow/<model|sinks>.csv
```

The query needs a `qlpack.yml` next to it declaring a dependency on `codeql/java-all`:

```yaml
name: local/regen-models
version: 0.0.0
dependencies:
  codeql/java-all: "*"
```

### `model.csv`

```ql
import java
import semmle.code.java.dataflow.internal.ExternalFlowExtensions

from string package, string type, boolean subtypes, string name, string signature, string ext,
     string input, string output, string kind, string provenance
where summaryModel(package, type, subtypes, name, signature, ext, input, output, kind, provenance, _)
select package, type, subtypes, name, signature, ext, input, output, kind, provenance
```

### `sinks.csv`

```ql
import java
import semmle.code.java.dataflow.internal.ExternalFlowExtensions

from string package, string type, boolean subtypes, string name, string signature, string ext,
     string input, string kind, string provenance
where sinkModel(package, type, subtypes, name, signature, ext, input, kind, provenance, _)
select package, type, subtypes, name, signature, ext, input, kind, provenance
```

> The trailing `_` binds CodeQL's `madId` extension column, which these predicates gained in newer
> releases. The import is `...dataflow.internal.ExternalFlowExtensions` (it moved under `internal`).
