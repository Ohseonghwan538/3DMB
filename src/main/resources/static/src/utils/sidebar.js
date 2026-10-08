async function loadSidebar() {
  const sidebarElement = document.getElementById('sidebar');
  if (!sidebarElement) return;

  try {
    const response = await fetch('/src/components/sidebar.html');
    if (!response.ok) {
      throw new Error(`Failed to load sidebar component: ${response.status}`);
    }

    sidebarElement.innerHTML = await response.text();

    const toggleButton = document.getElementById('tgl-sidebar');
    const closeButton = document.getElementById('btn-sidebar-close');
    const buildingToggle = document.getElementById('tgl-building');

    const setSidebarVisibility = (isOpen) => {
      sidebarElement.classList.toggle('is-hidden', !isOpen);
      if (toggleButton) {
        toggleButton.setAttribute('aria-expanded', String(isOpen));
      }
    };

    if (toggleButton) {
      toggleButton.addEventListener('click', () => {
        const isHidden = sidebarElement.classList.contains('is-hidden');
        setSidebarVisibility(isHidden);
      });
    }

    if (closeButton) {
      closeButton.addEventListener('click', () => setSidebarVisibility(false));
    }

    if (buildingToggle) {
      buildingToggle.addEventListener('click', (event) => {
        const button = event.currentTarget;
        const isCurrentlyChecked = button.getAttribute('aria-checked') === 'true';
        const newCheckedState = !isCurrentlyChecked;

        button.setAttribute('aria-checked', String(newCheckedState));
        
        window.dispatchEvent(new CustomEvent('toggleBuildingLayer', {
          detail: { visible: newCheckedState }
        }));
      });
    }
    document.dispatchEvent(new CustomEvent('sidebarLoaded'));
  } catch (error) {
    console.error('사이드바를 불러오지 못했습니다.', error);
    sidebarElement.innerHTML = '<div class="panel-section">도구 패널을 불러오지 못했습니다.</div>';
  }

  // 동적 로드되는 sidebar.html의 #create-memory 버튼 클릭 감지 (이벤트 위임)
  document.addEventListener('click', (event) => {
      const createBtn = event.target.closest('#btn-start-memory');
      if (!createBtn) return;
  
      console.log('✨ 새 추억 시작하기 버튼 클릭됨');
  
      const title = window.prompt('새 추억 묶음의 이름을 입력해 주세요.', '나의 3D 추억');
      if (title === null) return;
      const color = window.prompt('추억 묶음 색상을 입력해 주세요. (#RRGGBB)', '#4263EB');
      if (color === null) return;
      if (!/^#[0-9A-Fa-f]{6}$/.test(color)) {
        const feedback = document.getElementById('memory-feedback');
        if (feedback) feedback.textContent = '색상은 #RRGGBB 형식이어야 합니다.';
        return;
      }
      window.dispatchEvent(new CustomEvent('createMemoryGroup', {
        detail: { title: title.trim() || '나의 3D 추억', color }
      }));
  });
}

document.addEventListener('DOMContentLoaded', loadSidebar);
