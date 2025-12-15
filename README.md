<h1 align="center">OpenRewrite Analysis</h1>
<p align="center"><i></i><b>🎉 Automate software refactoring 🎉</b></i></p>

<div align="center">

<!-- Keep the gap above this line, otherwise they won't render correctly! -->
[![ci](https://github.com/openrewrite/rewrite-analysis/actions/workflows/ci.yml/badge.svg)](https://github.com/openrewrite/rewrite-analysis/actions/workflows/ci.yml)
[![Apache 2.0](https://img.shields.io/github/license/openrewrite/rewrite.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/org.openrewrite.meta/rewrite-analysis.svg)](https://mvnrepository.com/artifact/org.openrewrite.meta/rewrite-analysis)
[![Revved up by Develocity](https://img.shields.io/badge/Revved%20up%20by-Develocity-06A0CE?logo=Gradle&labelColor=02303A)](https://ge.openrewrite.org/scans)
</div>

> [!WARNING]  
> There are some fundamental limitations to the implementation in this module, and we are currently [exploring an alternative implementation](https://docs.moderne.io/openrewrite-advanced-program-analysis/control-flow).
> We advise against starting new efforts using `rewrite-analysis`. 

## What is this?

This project contains a series of utility functions and visitors to perform complex analysis of source code.

For example:
 - Data flow analysis
 - Control Flow Analysis
 - Fluent AST navigation with an trait-based API similar to [CodeQL](https://codeql.github.com).

## How to use?

See the full documentation at [docs.openrewrite.org](https://docs.openrewrite.org/).

## Refactoring at scale with Moderne

OpenRewrite's refactoring engine and recipes will always be open source. Build tool plugins like [OpenRewrite Gradle Plugin](https://docs.openrewrite.org/reference/gradle-plugin-configuration) and [OpenRewrite Maven Plugin](https://docs.openrewrite.org/reference/rewrite-maven-plugin) help you run these recipes on one repository at a time. Moderne is a complementary product that executes OpenRewrite recipes at scale on hundreds of millions of lines of code and enables mass committing of results. Moderne freely runs a [public service](https://public.moderne.io) for the benefit of thousands of open source projects.

[![Moderne](./doc/video_preview.png)](https://youtu.be/Mq6bKAeGCz0)

## Contributing

We appreciate all types of contributions. See the [contributing guide](https://github.com/openrewrite/.github/blob/main/CONTRIBUTING.md) for detailed instructions on how to get started.
