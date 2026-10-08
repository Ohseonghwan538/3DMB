const apiBaseUrl = window.location.origin;

export const imageApi = {
    async uploadImage(file) {
        const formData = new FormData();
        formData.append('file', file);
        const response = await fetch(`${apiBaseUrl}/api/media/images`, {
            method: "POST",
            headers: { 'X-CSRF-TOKEN': window.csrfToken || '' },
            body: formData
        });
        if (!response.ok) {
            const body = await response.json().catch(() => ({}));
            throw new Error(body.message || `업로드 실패 (HTTP ${response.status})`);
        }
        return response.json();
    }
};
