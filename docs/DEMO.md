# Faculty demonstration runbook

**Lab 04 – Arithmetic Operations · Git → Jenkins → Docker → Kubernetes**

Target length: **8–10 minutes**. The application is already deployed, but the demo should still show a *fresh* Jenkins build and a *fresh* pod rollout so the faculty sees the whole path rather than a static page.

---

## 0. Before the demo (2 minutes, do this while waiting)

Open these tabs/windows in order:

1. GitHub repository: <https://github.com/Ksiri03/Arithmetic-Operations>
2. Jenkins: <http://localhost:8080> → job **Arithmetic-Operations**
3. Live application: <http://localhost:30081>
4. A PowerShell window at `E:\devOps project\lab_external\Arithmetic-Operations`
5. Docker Desktop, with **Containers** visible

Check the environment:

```powershell
cd "E:\devOps project\lab_external\Arithmetic-Operations"
docker info --format '{{.ServerVersion}}'
kind get clusters
kubectl -n arithmetic get pods,svc
```

Expected:

- Docker engine prints a version (e.g. `29.7.2`).
- `arithmetic-lab` is listed once.
- Two pods are `1/1 Running`.
- Service `arithmetic-service` is `NodePort 80:30081/TCP`.

If anything is down, use the recovery section at the end **before** calling the faculty.

### If the machine was restarted

Start Docker Desktop first, then bring the kind node back if it is stopped:

```powershell
docker start arithmetic-lab-control-plane
kubectl -n arithmetic rollout status deployment/arithmetic-app --timeout=180s
```

Start Jenkins in a **separate PowerShell window** and leave the window open:

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

Sign in with `admin` / `arithmetic04`. If the application pods do not recover, run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\deploy-kind.ps1 -ImageTag build-4
```

---

## 1. Opening – what was built (60 seconds)

**Say:**

> The assigned question is arithmetic operations: addition, subtraction, multiplication and division. I built a small Java service with a browser console. The Java service does the arithmetic, the browser is only the interface. The same artifact moves through Git, Jenkins, Docker and Kubernetes. The public repository is here.

Show the GitHub repository. Point out:

- `src/main/java` – Java service
- `Dockerfile` – multi-stage image
- `Jenkinsfile` – complete CI/CD pipeline
- `k8s/` – Deployment and NodePort Service
- `docs/screenshots/` – evidence

**Do not read the code line by line.** The faculty wants the pipeline.

---

## 2. The running application (90 seconds)

Open <http://localhost:30081>.

1. Perform `128 + 64` → **192**.
2. Select **Subtract**, `128 − 64` → **64**.
3. Select **Multiply**, `12 × 8` → **96**.
4. Select **Divide**, `144 ÷ 12` → **12**.

Point at **Recent calculations**: all four operations are recorded. Point at the **Server** card:

- **Replica** – the actual Kubernetes pod name.
- **Node** – `arithmetic-lab-control-plane`.
- **Version** – `1.0.0`.
- **Uptime** – how long the serving replica has been running.
- **Online** – the health status returned by `/api/health`.

**Say:**

> The result is not calculated in JavaScript. The browser posts to `/api/calculate`; the Java service computes it with `BigDecimal` and returns JSON. Kubernetes routes the request to one of two replicas, and the replica name proves which pod served it.

Optional: show the API directly.

```powershell
curl.exe "http://localhost:30081/api/calculate?a=144&b=12&op=divide"
curl.exe "http://localhost:30081/api/health"
```

---

## 3. Jenkins – build the pipeline live (2–3 minutes)

Switch to Jenkins → **Arithmetic-Operations** → **Build Now**.

Open the new build → **Console Output**. While it runs, explain the stages in order:

| Stage | What the faculty should see |
|---|---|
| Checkout | `git checkout` of the `main` branch from GitHub |
| Compile and test | `11 passed, 0 failed` |
| Build Docker image | `arithmetic-ops:build-<n>` and `arithmetic-ops:latest` |
| Load image into kind | `Image ... loading` |
| Deploy to Kubernetes | `deployment.apps/arithmetic-app image updated`, then `successfully rolled out` |
| Smoke test | Four PASS lines and `Smoke test passed` |
| Finish | `Finished: SUCCESS` |

**Say:**

> Jenkins is not just running the program. It is the release gate: it clones the exact commit, compiles, runs eleven tests, builds a versioned image, loads that image into the local kind cluster, updates the Deployment and verifies the live service with four real API calls. If any test or smoke check fails, the build stops and the old version stays live.

If the faculty asks to see the job configuration, open **Configure** and show:

- **Pipeline script from SCM**
- Repository URL
- Branch `*/main`
- Script path `Jenkinsfile`

> The manual Freestyle version of the same job is documented in [`JENKINS.md`](JENKINS.md#3-manual-freestyle-job-lab-record-equivalent).

---

## 4. Docker – prove the artifact (1–2 minutes)

In PowerShell:

```powershell
docker images | findstr arithmetic-ops
docker run --rm -d --name arithmetic-demo -p 8082:8080 arithmetic-ops:1.0.0
Start-Sleep -Seconds 3
curl.exe "http://localhost:8082/api/calculate?a=12&b=8&op=multiply"
docker rm -f arithmetic-demo
```

Explain the image:

- **Stage 1** uses `eclipse-temurin:21-jdk-alpine` to compile the source and package a jar.
- **Stage 2** uses `eclipse-temurin:21-jre-alpine`; the compiler and source are not shipped.
- Runs as **UID 10001**, non-root.
- Read-only root filesystem and a small in-memory `/tmp` in Kubernetes.
- `ENTRYPOINT ["java", "-jar", "/app/app.jar"]`.

**Say:**

> The same image runs on my machine in Docker and inside Kubernetes. The only difference is where it is placed. That is the point of containerisation: one artifact, consistent behaviour.

---

## 5. Kubernetes – show the orchestration (2 minutes)

```powershell
kubectl -n arithmetic get deployment,pods,svc -o wide
kubectl -n arithmetic rollout status deployment/arithmetic-app
kubectl -n arithmetic describe pod -l app=arithmetic-app | findstr /C:"Image:" /C:"Node:" /C:"Ready:"
```

Point out:

- Deployment desired `2/2`, both pods `Running`.
- Both pods are scheduled on `arithmetic-lab-control-plane` (single-node kind).
- Service `NodePort 80:30081/TCP` exposes the pods on the host.
- The Deployment has readiness and liveness probes on `/api/health`.
- Resource requests/limits and the security context.

Show a rollout without changing code:

```powershell
kubectl -n arithmetic rollout restart deployment/arithmetic-app
kubectl -n arithmetic rollout status deployment/arithmetic-app --timeout=180s
kubectl -n arithmetic get pods
```

**Say:**

> Kubernetes is told the desired state: two replicas of this image. It performs a rolling update with `maxUnavailable: 0`, so one pod is always available. The readiness probe decides when a new pod can receive traffic; the liveness probe restarts a stuck container. If a pod is deleted, the ReplicaSet creates another one.

Refresh <http://localhost:30081> and calculate one operation. The Server card should show a new pod name.

Optional scale proof:

```powershell
kubectl -n arithmetic scale deployment/arithmetic-app --replicas=3
kubectl -n arithmetic get pods
kubectl -n arithmetic scale deployment/arithmetic-app --replicas=2
```

---

## 6. Close – result and evidence (30 seconds)

**Say:**

> The question is complete end to end: the Java program is on GitHub, Jenkins builds and tests it, Docker packages it, and Kubernetes runs two replicas behind a NodePort service. The writeup contains the commands, real terminal output and the output screens for each stage.

Show the final Jenkins success screen and the live application once more. Stop talking; let the faculty ask questions.

---

## Viva questions and short answers

**Why did you not use Spring Boot?**
> The arithmetic service needs one HTTP endpoint and static files. `com.sun.net.httpserver` is part of the JDK, so the build has zero external dependencies and works offline. It also makes the Docker image and the Jenkins build smaller and easier to explain. For a larger service I would use Spring Boot or Quarkus.

**Why `BigDecimal` instead of `double`?**
> `double` is binary floating point and cannot represent values such as `0.1` exactly. `BigDecimal` gives exact decimal results for the operations in this lab. Division uses scale 10 with `HALF_UP`, and division by zero is rejected explicitly.

**What happens if the divisor is zero?**
> The service returns HTTP 400 with a clear error: `Cannot divide by zero.` The UI shows the message and does not add the operation to the recent calculations list. The test suite covers this case.

**What is the difference between the image tag and `latest`?**
> Jenkins tags every build with an immutable `build-<n>` tag and also updates `latest` for convenience. Kubernetes is updated with the immutable tag, so the rollout is deterministic. Reusing `latest` with `imagePullPolicy: IfNotPresent` can leave old pods running the old image.

**How does Jenkins deploy to Kubernetes?**
> The pipeline loads the image into the kind node with `kind load docker-image`, applies the manifests with `kubectl apply -k k8s`, sets the new image on the Deployment, waits for `rollout status`, and runs the smoke test against the NodePort.

**Why two replicas?**
> It demonstrates a rolling update and service load balancing, and it removes a single point of failure. The history is deliberately per replica; the pod name in the UI shows which replica answered.

**What is a NodePort?**
> A Service type that opens the same port on every cluster node. Here port `30081` on the kind container is mapped to host `30081`, and the Service forwards to port `8080` in the pods. In production I would use an Ingress with a DNS name and TLS.

**How would you productionise this?**
> Push the image to a registry instead of `kind load`, use immutable digests, add an Ingress and TLS, use Helm or Kustomize overlays per environment, add HPA, network policies, PodDisruptionBudget, externalised configuration/secrets, structured logs and metrics, and a real integration-test stage.

**Where is the application state stored?**
> Only the last 12 calculations per replica are kept in memory, on purpose, to demonstrate routing. A restart clears the recent list. A real application would use a database or Redis and would not keep state in the pod.

---

## Recovery during the demo

### Jenkins built-in node is offline

```powershell
Get-Process java | Select-Object Id, Path
# Restart Jenkins with the E: temp directory (see docs/JENKINS.md)
```

### Docker is not running

Start Docker Desktop and wait for **Engine running**:

```powershell
docker info
```

### kind cluster is missing

```powershell
kind create cluster --name arithmetic-lab --config k8s\kind-config.yaml
kind load docker-image arithmetic-ops:1.0.0 --name arithmetic-lab
kubectl apply -k k8s
kubectl -n arithmetic rollout status deployment/arithmetic-app --timeout=180s
```

### App is unreachable on 30081

```powershell
kubectl -n arithmetic get pods,svc
kubectl -n arithmetic describe deployment/arithmetic-app
kubectl -n arithmetic logs deployment/arithmetic-app --tail=50
```

### Port 30081 / 8080 is occupied

```powershell
Get-NetTCPConnection -LocalPort 30081,8080 -ErrorAction SilentlyContinue |
  Select-Object LocalPort, OwningProcess
```

Change `nodePort` in `k8s/service.yaml` and the `extraPortMappings` in `k8s/kind-config.yaml`, then recreate the cluster. For Jenkins, start the WAR with a different `--httpPort`.

### A build fails at the smoke test

```powershell
kubectl -n arithmetic get pods -o wide
kubectl -n arithmetic get events --sort-by=.lastTimestamp
kubectl -n arithmetic logs deployment/arithmetic-app --tail=100
```

The previous version remains live because the Deployment rolls out only after the new pods become ready.

---

## One-command reset (between rehearsals)

```powershell
kubectl -n arithmetic rollout restart deployment/arithmetic-app
kubectl -n arithmetic rollout status deployment/arithmetic-app --timeout=180s
```

This clears the in-memory recent list on both replicas. The first page load records `128 + 64 = 192`; add subtract, multiply and divide to produce a clean four-row list for the screenshot.
