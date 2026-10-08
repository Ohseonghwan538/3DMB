const card = document.getElementById('shared-card');
const token = location.pathname.split('/').filter(Boolean).at(-1);

async function loadSharedPoi() {
  const response = await fetch(`/api/shared/${encodeURIComponent(token)}`, { headers: { Accept: 'application/json' } });
  if (!response.ok) {
    card.replaceChildren();
    const message = document.createElement('p');
    message.textContent = response.status === 404
      ? '공유 링크가 만료되었거나 취소되었습니다.'
      : '공유된 추억을 불러오지 못했습니다.';
    card.append(message);
    return;
  }
  const poi = await response.json();
  const title = document.createElement('h1');
  title.className = 'shared-title';
  title.style.backgroundColor = /^#[0-9A-Fa-f]{6}$/.test(poi.groupColor || '') ? poi.groupColor : '#4263EB';
  title.textContent = poi.title || poi.groupName || '공유된 추억';
  const image = document.createElement('img');
  image.className = 'shared-cover';
  image.src = poi.imageUrl;
  image.alt = poi.title || '공유된 추억 사진';
  const content = document.createElement('div');
  content.className = 'shared-content';
  content.textContent = poi.content || '';
  const meta = document.createElement('div');
  meta.className = 'shared-meta';
  meta.textContent = `${poi.groupName} · ${Number(poi.longitude).toFixed(6)}, ${Number(poi.latitude).toFixed(6)}`;
  card.replaceChildren(title, image, content, meta);
}

loadSharedPoi().catch(() => {
  const message = document.createElement('p');
  message.textContent = '공유된 추억을 불러오지 못했습니다.';
  card.replaceChildren(message);
});
