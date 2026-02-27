# Batch Call System (Interview Repo)

## Purpose

This repository is used for backend engineer interviews. Candidate instructions live in `INTERVIEW.md`, while `ANSWER.md` is an internal answer key.

## Development setup

```sh
./gradlew bootRun
```

The server listens on port 3456 by default. See `INTERVIEW.md` for sample API usage.

## Build interviewee package

We distribute a zip that excludes internal artifacts like `ANSWER.md` via `git archive` and `.gitattributes`.

```sh
./scripts/build-interviewee.sh
```

Output: `dist/interviewee.zip`

### Notes

- `.gitattributes` uses `export-ignore` to omit `ANSWER.md` and other internal artifacts from the archive.
- `INTERVIEW.md` is included in the archive and should be shared with candidates.

## Scenario tests

The repo includes a small scenario test runner that describes expected behavior in realistic cases.

```sh
./scripts/scenario-tests.sh
```

To run a single case:

```sh
./scripts/scenario-tests.sh case-a
```
