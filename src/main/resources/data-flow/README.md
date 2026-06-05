# Data Flow and Taint Tracking Model

`model.csv` lists methods that transfer data flow or taint; `sinks.csv` lists methods whose arguments
(or return value) are sinks. Both are generated from [CodeQL](https://codeql.github.com/)'s Java models.

Notes:
- The CSV columns are the CodeQL model columns. `ext` and `provenance` are retained in the files for
  fidelity with CodeQL but are **not** read by the loader (`ExternalFlowModels` / `ExternalSinkModels`).
- Current CodeQL writes the receiver/qualifier as `Argument[this]` (older versions used `Argument[-1]`);
  the loader understands both. The argument selector may also be a comma-separated union and/or a range,
  e.g. `Argument[this,0]` or `Argument[0..2]`.
- The files are kept as-is from CodeQL; how the engine interprets content and higher-order paths is
  described under [Content and higher-order paths](#content-and-higher-order-paths) below.

## Content and higher-order paths

An access path may name a *part* of a value rather than the whole value:

- **Content** components — `Element` (collection/iterable), `ArrayElement`, `MapKey`/`MapValue`,
  `Field[...]`, `SyntheticField[...]` — name an interior slot of a container.
- **Callback** components — `Argument[i].Parameter[j]` and `Argument[i].ReturnValue` — describe flow
  into/out of a functional argument (a lambda).

This engine is **content-insensitive**: it collapses every content component onto its container. A
store such as `Collection.add: Argument[0] -> Argument[this].Element` becomes `Argument[0] ->
Argument[this]` (the value taints the whole collection), and a read such as `List.get:
Argument[this].Element -> ReturnValue` becomes `Argument[this] -> ReturnValue`. The store and read
still reconnect — at container granularity instead of slot granularity — so this is a sound
over-approximation: it can add false positives (e.g. a tainted map key makes value reads look tainted)
but never loses a flow, *provided every store and read collapses uniformly*. Callback paths are handled
separately (see `CallbackFlowModel`); the few `WithElement`/`WithoutElement` typestate paths are not
modeled and are ignored.

Two known limitations, each a potential follow-up:

1. **Generic signatures.** A model with an explicit signature at a generic position (e.g.
   `Map.put` spelled `(Object,Object)` for the declared `put(K,V)`) does not match a generic call site,
   because the matcher has no erasure for type-variable parameters. So container *writes* through such
   methods (notably `Map.put`) are not yet tracked, even though the reads are.
2. **Precision.** True content/field-sensitivity (tracking which slot is tainted, so `MapKey` stores
   don't leak to `MapValue` reads) would remove the false positives above. It is a larger change that
   would thread an access-path/content state through the flow graph, mirroring CodeQL.

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
