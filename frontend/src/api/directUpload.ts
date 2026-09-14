export class DirectUploadError extends Error {
  readonly status: number | null

  constructor(message: string, status: number | null = null) {
    super(message)
    this.status = status
  }
}

export interface DirectUploadRequest {
  url: string
  method: string
  requiredHeaders: Record<string, string>
  file: File
  onProgress: (percent: number) => void
}

export function uploadFileDirectly(request: DirectUploadRequest): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open(request.method, request.url, true)
    Object.entries(request.requiredHeaders).forEach(([name, value]) => {
      xhr.setRequestHeader(name, value)
    })
    xhr.upload.addEventListener('progress', (event) => {
      if (event.lengthComputable && event.total > 0) {
        request.onProgress(Math.min(100, Math.round(event.loaded / event.total * 100)))
      }
    })
    xhr.addEventListener('load', () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        request.onProgress(100)
        resolve()
      } else {
        reject(new DirectUploadError(`Object storage returned HTTP ${xhr.status}`, xhr.status))
      }
    })
    xhr.addEventListener('error', () => reject(new DirectUploadError('Object storage is unavailable')))
    xhr.addEventListener('timeout', () => reject(new DirectUploadError('Object storage request timed out')))
    xhr.send(request.file)
  })
}
