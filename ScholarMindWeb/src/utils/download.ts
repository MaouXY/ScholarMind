import axios, { type AxiosResponse } from 'axios'

import type { LiteratureDownloadTicket } from '@/types/literature'
import { getToken } from '@/utils/storage'

/**
 * 文件下载专用通道。
 * 全局 request 实例的响应拦截器会无条件按 JSON 解析 `{code,message,data}`，
 * 下载/导出接口返回的是 text/plain、text/markdown 附件，必须绕过它：
 * 这里单独创建 axios 实例，手动带上 Authorization: Bearer <token>，并以 blob 接收。
 */
const downloadClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 60000,
})

function authHeaders() {
  const token = getToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

/** 从 Content-Disposition 中解析后端指定的附件文件名（兼容 filename*=UTF-8'' 写法） */
export function resolveDownloadFileName(
  headers: unknown,
  fallback: string,
): string {
  const raw = (headers as Record<string, unknown> | undefined)?.[
    'content-disposition'
  ]
  if (typeof raw !== 'string' || !raw) {
    return fallback
  }

  const encoded = /filename\*=\s*UTF-8''([^;]+)/i.exec(raw)
  if (encoded?.[1]) {
    try {
      return decodeURIComponent(encoded[1].trim())
    } catch (error) {
      console.error(
        'download filename decode failed, scenario=download-filename-decode, raw=%s',
        encoded[1],
        error,
      )
    }
  }

  const plain = /filename=\s*"?(.+?)"?\s*(?:;|$)/i.exec(raw)
  return plain?.[1] ? plain[1].trim() : fallback
}

function sanitizeFileName(fileName: string, fallback: string) {
  const cleaned = fileName.replace(/[\\/:*?"<>|]+/g, '_').trim()
  return cleaned || fallback
}

function stripExtension(fileName: string) {
  const index = fileName.lastIndexOf('.')
  return index > 0 ? fileName.slice(0, index) : fileName
}

/**
 * 以 blob + Authorization 下载接口返回的附件。
 * filename 有值时优先使用，其次读取 Content-Disposition，最后回落到 fallbackFileName。
 */
export async function downloadAuthenticatedBlob(
  url: string,
  params: Record<string, string | number | undefined> = {},
  filename?: string,
  fallbackFileName = 'download',
): Promise<string> {
  const response: AxiosResponse<Blob> = await downloadClient.get(url, {
    params,
    responseType: 'blob',
    headers: authHeaders(),
  })

  const resolved = sanitizeFileName(
    filename || resolveDownloadFileName(response.headers, fallbackFileName),
    fallbackFileName,
  )
  const blobUrl = URL.createObjectURL(response.data)

  const link = document.createElement('a')
  link.href = blobUrl
  if (typeof link.download === 'string') {
    link.download = resolved
    link.rel = 'noopener'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  } else {
    // 不支持 download 属性的旧浏览器直接新开标签
    window.open(blobUrl, '_blank')
  }

  window.setTimeout(() => URL.revokeObjectURL(blobUrl), 0)
  return resolved
}

/** 导出参考文献附件（GET /citations/export?style=xxx，text/plain） */
export function downloadCitationReferences(style: string, styleLabel?: string) {
  return downloadAuthenticatedBlob(
    '/citations/export',
    { style },
    `${stripExtension(styleLabel || style)}-参考文献.txt`,
    `references-${style}.txt`,
  )
}

/** 导出阅读笔记附件（GET /notes/export?literatureId=xxx，text/markdown） */
export function downloadReadingNotes(literatureId?: string) {
  return downloadAuthenticatedBlob(
    '/notes/export',
    literatureId ? { literatureId } : {},
    'reading-notes.md',
    'reading-notes.md',
  )
}

/**
 * 打开文献原文：先换取 RustFS 预签名 URL，再用新标签页打开。
 * 文献未绑定原文时后端返回业务错误码 14107（拦截层已提示），这里再补一条友好提示。
 */
export async function openLiteratureFile(
  ticket: LiteratureDownloadTicket,
): Promise<LiteratureDownloadTicket> {
  const url = ticket.url
  if (!url) {
    throw new Error('原文地址为空')
  }

  const link = document.createElement('a')
  link.href = url
  link.target = '_blank'
  link.rel = 'noreferrer'
  if (ticket.fileName) {
    link.download = ticket.fileName
  }
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)

  return ticket
}
