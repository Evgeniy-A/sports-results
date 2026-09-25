export interface AttachmentDownloadAuthorization {
  downloadUrl: string
}

export type AttachmentWindowOpener = (
  url: string,
  target: string,
  features: string,
) => unknown

export async function authorizeAndOpenAttachment(
  authorize: () => Promise<AttachmentDownloadAuthorization>,
  openWindow: AttachmentWindowOpener = (url, target, features) => window.open(url, target, features),
): Promise<void> {
  const { downloadUrl } = await authorize()
  openWindow(downloadUrl, '_blank', 'noopener,noreferrer')
}
