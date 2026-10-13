# Third-party components

Digital Grader source is MIT licensed. Dependencies and infrastructure retain their own licenses.

The optional local MinIO image is built from the official AGPLv3-licensed MinIO community source. The exact release, commit, source URL and archive checksum are recorded in `infra/minio/Dockerfile`; its upstream LICENSE is included at `/usr/share/licenses/minio/LICENSE` inside the image. No MinIO source is vendored or relicensed by this repository.

Runtime dependencies are declared in the Maven POM and frontend package lock; container images and the MinIO build are declared in Docker Compose.
