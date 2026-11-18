# Jenkins configuration and evidence

This document records the Jenkins setup used for **Lab 04 – Arithmetic Operations** and gives the manual **Freestyle** configuration that can be shown in a lab record. The repository's real automation is the declarative [`Jenkinsfile`](../Jenkinsfile); the Freestyle steps are the equivalent low-level version.

## 1. What is running

| Item | Value |
|---|---|
| Jenkins URL | <http://localhost:8080> |
| Jenkins version | 2.580.2 |
| User | `admin` |
| Job name | `Arithmetic-Operations` |
| Job type | Pipeline (SCM) |
| Repository | <https://github.com/Ksiri03/Arithmetic-Operations> |
| Branch | `*/main` |
| Script path | `Jenkinsfile` |
| Last result | Build `#4` – **SUCCESS** (1 min 20 s) |
| Jenkins home | `E:\devOps project\lab_external\.jenkins\home` |

Jenkins runs as a host process on Windows because it must reach Docker Desktop, `kind` and `kubectl` directly. It is not containerised for this lab.

## 2. Pipeline job (used for the demo)

1. Open <http://localhost:8080> and sign in as `admin`.
2. Click **Arithmetic-Operations**.
3. Click **Build Now**.
4. Open the new build → **Console Output**.

The pipeline performs:

| Stage | Command / action |
|---|---|
| Checkout | Clones `main` from GitHub |
| Compile and test | `powershell -File scripts\test.ps1` – compiles the app and runs 11 tests |
| Build Docker image | `docker build -t arithmetic-ops:build-<n> -t arithmetic-ops:latest .` |
| Load image into kind | `kind load docker-image arithmetic-ops:build-<n> --name arithmetic-lab` |
| Deploy to Kubernetes | `kubectl apply -k k8s`, `kubectl set image`, `kubectl rollout status` |
| Smoke test | Four API operations on <http://localhost:30081> + health + history |

A successful console ends with:

```text
PASS 128 + 64 = 192 [arithmetic-app-...]
PASS 128 - 64 = 64 [arithmetic-app-...]
PASS 12 * 8 = 96 [arithmetic-app-...]
PASS 144 / 12 = 12 [arithmetic-app-...]
==> Smoke test passed: 4 operations + health + history
...
Deployed arithmetic-ops:build-4 to kind cluster arithmetic-lab. Open http://localhost:30081
Finished: SUCCESS
```

## 3. Manual Freestyle job (lab-record equivalent)

Use this when the faculty asks for the reference-style **New Item → Freestyle project** configuration instead of the pipeline.

### 3.1 Create the job

1. **New Item** → name `Arithmetic-Operations-Freestyle` → **Freestyle project** → **OK**.
2. **Source Code Management** → **Git**.
   - Repository URL: `https://github.com/Ksiri03/Arithmetic-Operations.git`
   - Branch Specifier: `*/main`
   - Credentials: none (public repository).
3. **Build Triggers** → leave disabled for a manual demo.
4. **Build Steps** → **Add build step** → **Execute Windows batch command**.
5. Paste the command block below.
6. **Save** → **Build Now** → **Console Output**.

### 3.2 Freestyle build command

```bat
echo ===== 1. Versions =====
java -version
git --version
docker --version

echo ===== 2. Compile and test =====
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\test.ps1

echo ===== 3. Build Docker image =====
docker build -t arithmetic-ops:freestyle-%BUILD_NUMBER% -t arithmetic-ops:latest .

echo ===== 4. List image =====
docker images | findstr arithmetic-ops

echo ===== 5. Run a container and test the API =====
docker rm -f arithmetic-freestyle >nul 2>&1
docker run -d --name arithmetic-freestyle -p 8082:8080 arithmetic-ops:latest
timeout /t 4 /nobreak >nul
powershell -NoProfile -Command "(Invoke-RestMethod 'http://localhost:8082/api/health').status"
powershell -NoProfile -Command "(Invoke-RestMethod 'http://localhost:8082/api/calculate?a=144&b=12&op=divide').result"
docker rm -f arithmetic-freestyle

echo ===== 6. Load into kind and deploy =====
kind load docker-image arithmetic-ops:freestyle-%BUILD_NUMBER% --name arithmetic-lab
kubectl apply -k k8s
kubectl -n arithmetic set image deployment/arithmetic-app app=arithmetic-ops:freestyle-%BUILD_NUMBER%
kubectl -n arithmetic rollout status deployment/arithmetic-app --timeout=180s
kubectl -n arithmetic get pods -o wide
```

If the faculty only wants the reference scope (compile + execute + Docker image), stop after step 5. Steps 6–7 demonstrate the additional Kubernetes part of this lab.

> `timeout /t 4` waits for the JVM to start before the API check. The arithmetic container starts in about 2–3 seconds.

### 3.3 Expected Freestyle console signature

```text
===== 2. Compile and test =====
...
11 passed, 0 failed
==> All tests passed
===== 3. Build Docker image =====
...
naming to docker.io/library/arithmetic-ops:freestyle-3 done
===== 4. List image =====
arithmetic-ops    freestyle-3    ...    ...MB
===== 5. Run a container and test the API =====
ok
12
===== 6. Load into kind and deploy =====
...
deployment "arithmetic-app" successfully rolled out
```

## 4. Jenkins installation notes (Windows)

The lab machine uses the Jenkins WAR directly, with its home and temporary directory on `E:` because the system drive is small.

The exact command line used by the running process is:

```powershell
$env:JENKINS_HOME = "E:\devOps project\lab_external\.jenkins\home"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

& "$env:JAVA_HOME\bin\java.exe" `
  '-Djava.io.tmpdir=E:\devOps project\lab_external\.jenkins\tmp' `
  -Djenkins.install.runSetupWizard=false `
  -jar 'E:\devOps project\lab_external\.jenkins\jenkins.war' `
  --httpPort=8080
```

The built-in node stays online because `-Djava.io.tmpdir` points to the same `E:` volume as `JENKINS_HOME`; the default temp directory on `C:` caused the node to go offline when the drive filled.

Initial admin password:

```text
E:\devOps project\lab_external\.jenkins\home\secrets\initialAdminPassword
```

The account used for the recorded demo is `admin` / `arithmetic04`.

### Required plugins

- Git
- Pipeline
- Pipeline: Stage View (optional, for a visual stage graph)
- Timestamper (used by `options { timestamps() }`)
- Workspace Cleanup (optional)

### Tools on the Jenkins PATH

`java`, `javac`, `jar`, `git`, `docker`, `kind`, `kubectl`, `powershell`.

The build scripts discover a complete JDK through `scripts/env.ps1`. This is necessary because the Oracle `javapath` shim provides `java` and `javac` but not `jar`.

## 5. Troubleshooting

| Symptom | Fix |
|---|---|
| Built-in node is offline | Check free space on the Jenkins temp volume; restart Jenkins with `-Djava.io.tmpdir` on `E:`. |
| `jar` is not recognised | Jenkins PATH is using the Oracle shim. Set `JAVA_HOME` to a full JDK or let `scripts/env.ps1` find one. |
| `docker: error during connect` | Start Docker Desktop and wait for the engine. |
| `kind: command not found` | Add the kind binary directory to the Jenkins process PATH, then restart Jenkins. |
| `rollout status` times out | `kubectl -n arithmetic describe deployment/arithmetic-app`; `kubectl -n arithmetic get pods`; check the image was loaded into kind. |
| Smoke test gets connection refused | Wait for both pods to be `1/1 Running`; check `kubectl -n arithmetic get svc`. The script retries the first health request for up to 48 seconds to cover the NodePort readiness gap during a rollout. |
| Port 30081 already in use | Edit `k8s/service.yaml` and `k8s/kind-config.yaml`, then rerun the pipeline. |
| Image tag not updated in cluster | Use an immutable tag (`build-<n>`) and `kubectl set image`; do not rely on `latest` with `imagePullPolicy: IfNotPresent`. |

## 6. Evidence captured

Current build `#4` evidence:

- [`screenshots/jenkins-dashboard-v2.png`](screenshots/jenkins-dashboard-v2.png)
- [`screenshots/jenkins-job-v2.png`](screenshots/jenkins-job-v2.png)
- [`screenshots/jenkins-build-v2.png`](screenshots/jenkins-build-v2.png)
- [`screenshots/jenkins-console-top-v2.png`](screenshots/jenkins-console-top-v2.png) – pipeline stages and the start of build `#4`
- [`screenshots/jenkins-console-tests-v2.png`](screenshots/jenkins-console-tests-v2.png) – the 11/11 test result inside the Jenkins console
- [`screenshots/jenkins-console-success-v2.png`](screenshots/jenkins-console-success-v2.png) – smoke checks and `Finished: SUCCESS`
- [`jenkins-build-4-console.txt`](jenkins-build-4-console.txt) – complete console log for build `#4`

Earlier build `#2` evidence, kept for the legacy write-up:

- [`screenshots/jenkins-dashboard.png`](screenshots/jenkins-dashboard.png)
- [`screenshots/jenkins-job.png`](screenshots/jenkins-job.png)
- [`screenshots/jenkins-build.png`](screenshots/jenkins-build.png)
- [`screenshots/jenkins-console-top.png`](screenshots/jenkins-console-top.png)
- [`screenshots/jenkins-console-tests.png`](screenshots/jenkins-console-tests.png)
- [`screenshots/jenkins-console-success.png`](screenshots/jenkins-console-success.png)
- [`jenkins-build-2-console.txt`](jenkins-build-2-console.txt)
