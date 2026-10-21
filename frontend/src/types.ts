export type Space = 'knowledge' | 'thoughts' | 'diary'
export interface Note {
  id: string
  title: string
  content: string
  space: Space
  category: string
  tags: string[]
  favorite: boolean
  published: boolean
  draft: boolean
  shareId: string
  entryDate: string
  createdAt: string
  updatedAt: string
  version: number
}
export interface Session {
  authenticated: boolean
  name: string
  localLogin: boolean
  oidc: boolean
  publicAccess: boolean
}
