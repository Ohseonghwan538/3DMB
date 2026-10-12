function addText(parent, tag, className, value) {
  const element = document.createElement(tag);
  if (className) element.className = className;
  element.textContent = value || "정보 없음";
  parent.append(element);
  return element;
}

export function createInfoElement() {
  const card = document.createElement("section");
  card.className = "festival-info-card";
  card.hidden = true;
  card.setAttribute("aria-label", "축제 정보");

  const close = document.createElement("button");
  close.type = "button";
  close.className = "info-close";
  close.setAttribute("aria-label", "정보창 닫기");
  close.textContent = "×";
  card.append(close);

  const title = addText(card, "h2", "info-title", "");
  const meta = document.createElement("div");
  meta.className = "info-meta";
  const venue = addText(meta, "p", "info-venue", "");
  const address = addText(meta, "p", "info-address", "");
  const period = addText(meta, "p", "info-period", "");
  const organizer = addText(meta, "p", "info-organizer", "");
  card.append(meta);
  const content = addText(card, "p", "info-content", "");
  const error = document.createElement("div");
  error.className = "info-error";
  error.hidden = true;
  card.append(error);
  return { card, close, title, venue, address, period, organizer, content, error };
}

export function renderInfo(element, festival) {
  element.title.textContent = festival.name || "정보 없음";
  element.venue.textContent = `장소: ${festival.venue || "정보 없음"}`;
  element.address.textContent = `주소: ${festival.roadAddr || "정보 없음"}`;
  element.period.textContent = `기간: ${festival.startDate || "정보 없음"} ~ ${festival.endDate || "정보 없음"}`;
  element.organizer.textContent = `주최 / 주관: ${festival.hostOrg || "정보 없음"} / ${festival.mngOrg || "정보 없음"}`;
  element.content.textContent = festival.content || "정보 없음";
  element.error.replaceChildren();
  element.error.hidden = true;
}

export function renderInfoError(element, message, retry) {
  element.error.replaceChildren();
  const text = document.createElement("p");
  text.textContent = message;
  element.error.append(text);
  const button = document.createElement("button");
  button.type = "button";
  button.className = "info-retry";
  button.textContent = "다시 시도";
  button.addEventListener("click", retry, { once: true });
  element.error.append(button);
  element.error.hidden = false;
}
