# Development guidelines for Claude

## Control flow changes

Any pull request that modifies control flow graph construction or visualization **must** include rendered CFG images in the PR description to allow reviewers to visually verify the graph is correct.

### Requirements

- Provide **at least two** examples:
  - One **simple** case that clearly illustrates the change (e.g., a minimal try-catch with a couple of statements)
  - One **more complex** case that exercises the change in a realistic context (e.g., nested conditions, multi-catch, finally blocks)
- Images must be attached directly to the PR description — do **not** commit PNG files to the repository.

### How to generate images

Use `ControlFlowDotGeneratorTest` to produce DOT files for any Java snippet, then convert them with Graphviz:

```bash
# 1. Run the DOT generator (add test methods to ControlFlowDotGeneratorTest as needed)
./gradlew test --tests "*.ControlFlowDotGeneratorTest" -Pcfg.dot.output.dir=build/cfg-dot

# 2. Convert to PNG
for f in build/cfg-dot/*.dot; do dot -Tpng -o "${f%.dot}.png" "$f"; done
```

To generate DOT output for any existing test without modifying it, add a corresponding entry to `ControlFlowDotGeneratorTest` with the same source snippet and run as above.

### Why

CFG construction logic is subtle. Visual verification makes it easy to catch mistakes like wrong edge directions, missing exception paths, or incorrectly split/merged basic blocks — things that are hard to spot by reading node counts alone.
