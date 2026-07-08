const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export async function fetchJson<T>(path: string, params?: Record<string, string>): Promise<T> {
	const url = new URL(path, API_BASE_URL);
	if (params) {
		for (const [key, value] of Object.entries(params)) {
			url.searchParams.set(key, value);
		}
	}
	const response = await fetch(url);
	if (!response.ok) {
		throw new Error(`API error: ${response.status} ${response.statusText} (${path})`);
	}
	return (await response.json()) as T;
}
