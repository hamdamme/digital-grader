# Project naming and existing installations

The project name is Digital Grader. The repository, build artifacts, image names and configuration prefixes use `digital-grader`; Java packages use `com.digitalgrader`.

The GitHub repository is [hamdamme/digital-grader](https://github.com/hamdamme/digital-grader). Its rename and the local folder rename are complete. The repository retains its Git history and original repository ID. Existing clones should use the current remote:

```sh
git remote set-url origin git@github.com:hamdamme/digital-grader.git
```

## Preserve existing data

Existing credentials, database names and persisted service data do not need renaming. For an existing installation, `COMPOSE_PROJECT_NAME` can retain the original Compose project identity and `SUBMISSION_QUEUE` can retain the existing queue. Set these in the ignored `.env` when needed. Both publisher and listener honor `SUBMISSION_QUEUE`.

Fresh installations use defaults from `.env.example` and `docker-compose.yml`. Do not delete volumes or regenerate credentials to change their names.

Restart the API and worker together when adopting the renamed build. Configuration properties now start with `digital-grader`; use the updated worker launcher. Access JWTs with the former issuer require login or refresh. Refresh token storage and password hashes are unchanged.

Rebuild images using the current documented tags. See [backend setup](SESSION_2.md) and [sandbox setup](SESSION_6.md). The rename does not complete the outstanding Linux acceptance checks.
