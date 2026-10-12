const importForm = document.getElementById("import-form");
const fileInput = document.getElementById("csv-file");
const fileName = document.getElementById("file-name");
const importButton = document.getElementById("import-button");
const importMessage = document.getElementById("import-message");
const importResult = document.getElementById("import-result");
const historyBody = document.getElementById("history-body");
const historyMessage = document.getElementById("history-message");

function csrfToken() {
  const cookie = document.cookie.split("; ").find((part) => part.startsWith("XSRF-TOKEN="));
  return cookie ? decodeURIComponent(cookie.slice("XSRF-TOKEN=".length)) : "";
}

async function api(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: { Accept: "application/json", ...options.headers },
    signal: AbortSignal.timeout(30_000)
  });
  const body = await response.json().catch(() => null);
  if (!response.ok) throw new Error(body?.message || `요청에 실패했습니다 (${response.status}).`);
  return body;
}

function renderImportResult(result) {
  importResult.replaceChildren();
  const stats = document.createElement("div");
  stats.className = "result-stats";
  for (const [label, value] of [
    ["전체 행", result.totalRows], ["성공", result.successRows], ["건너뜀", result.skippedRows]
  ]) {
    const cell = document.createElement("div");
    cell.className = "result-stat";
    const name = document.createElement("span");
    name.textContent = label;
    const number = document.createElement("strong");
    number.textContent = Number(value).toLocaleString("ko-KR");
    cell.append(name, number);
    stats.append(cell);
  }
  importResult.append(stats);
  if (result.skipped?.length) {
    const heading = document.createElement("strong");
    heading.textContent = "건너뛴 행";
    const list = document.createElement("ol");
    list.className = "skipped-list";
    for (const skipped of result.skipped) {
      const item = document.createElement("li");
      item.textContent = `${skipped.row}행 · ${skipped.reason}`;
      list.append(item);
    }
    if (result.skippedRows > result.skipped.length) {
      const more = document.createElement("li");
      more.textContent = `나머지 ${result.skippedRows - result.skipped.length}건은 서버 로그에서 확인할 수 있습니다.`;
      list.append(more);
    }
    importResult.append(heading, list);
  }
  importResult.hidden = false;
}

function formatDate(value) {
  if (!value) return "정보 없음";
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "short", timeStyle: "short"
  }).format(new Date(value));
}

async function loadHistory() {
  historyMessage.textContent = "이력을 불러오는 중입니다.";
  historyMessage.classList.remove("is-error");
  try {
    const entries = await api("/admin/api/imports");
    historyBody.replaceChildren();
    if (entries.length === 0) {
      historyMessage.textContent = "아직 적재 이력이 없습니다.";
      return;
    }
    for (const entry of entries) {
      const row = document.createElement("tr");
      for (const value of [
        entry.fileName,
        Number(entry.totalRows).toLocaleString("ko-KR"),
        Number(entry.successRows).toLocaleString("ko-KR"),
        Number(entry.skippedRows).toLocaleString("ko-KR"),
        formatDate(entry.importedAt)
      ]) {
        const cell = document.createElement("td");
        cell.textContent = value;
        row.append(cell);
      }
      historyBody.append(row);
    }
    historyMessage.textContent = `최근 ${entries.length}건`;
  } catch (error) {
    historyMessage.textContent = error.message;
    historyMessage.classList.add("is-error");
  }
}

fileInput.addEventListener("change", () => {
  fileName.textContent = fileInput.files?.[0]?.name || "CSV 파일을 선택하세요";
});

importForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const file = fileInput.files?.[0];
  if (!file) return;
  if (!file.name.toLowerCase().endsWith(".csv")) {
    importMessage.textContent = "CSV 파일(.csv)만 선택할 수 있습니다.";
    importMessage.classList.add("is-error");
    return;
  }
  if (file.size > 5 * 1024 * 1024) {
    importMessage.textContent = "파일 크기는 5MB를 초과할 수 없습니다.";
    importMessage.classList.add("is-error");
    return;
  }
  importButton.disabled = true;
  importMessage.textContent = "CSV를 검증하고 데이터베이스에 적재하고 있습니다.";
  importMessage.classList.remove("is-error");
  importResult.hidden = true;
  const formData = new FormData(importForm);
  try {
    const result = await api("/admin/api/import", {
      method: "POST",
      headers: { "X-XSRF-TOKEN": csrfToken() },
      body: formData
    });
    importMessage.textContent = "적재가 완료되었습니다.";
    renderImportResult(result);
    await loadHistory();
  } catch (error) {
    importMessage.textContent = error.message;
    importMessage.classList.add("is-error");
  } finally {
    importButton.disabled = false;
  }
});

document.getElementById("refresh-history").addEventListener("click", () => void loadHistory());
void loadHistory();
