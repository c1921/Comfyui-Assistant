import { ref } from 'vue'
import type { AlbumItem } from '../types/app'
import { deleteAlbumItem as deleteAlbumApi, getAlbum } from '../services/api'

type AlbumOptions = {
  baseHttp: () => string
  backendOrigin: () => string
}

export const useAlbum = ({ baseHttp, backendOrigin }: AlbumOptions) => {
  const albumImages = ref<AlbumItem[]>([])
  const albumError = ref('')
  const albumSortOrder = ref<'asc' | 'desc'>('desc')

  const normalizeAlbumItems = (items: AlbumItem[]) =>
    items.map((item) => ({
      name: item.name,
      url: item.url.startsWith('http') ? item.url : `${backendOrigin()}${item.url}`,
    }))

  const fetchAlbum = async () => {
    albumError.value = ''
    try {
      const data = await getAlbum(baseHttp(), albumSortOrder.value)
      if (Array.isArray(data)) {
        albumImages.value = normalizeAlbumItems(data)
      } else {
        albumImages.value = []
      }
    } catch (err) {
      albumError.value = `相册加载失败：${(err as Error)?.message || String(err)}`
      albumImages.value = []
    }
  }

  const deleteAlbumItem = async (item: AlbumItem) => {
    const ok = confirm(`删除图片 "${item.name}"？此操作会删除磁盘文件。`)
    if (!ok) return
    try {
      await deleteAlbumApi(baseHttp(), item.name)
      albumImages.value = albumImages.value.filter((img) => img.name !== item.name)
    } catch (err) {
      albumError.value = `删除失败：${(err as Error)?.message || String(err)}`
    }
  }

  return {
    albumImages,
    albumError,
    albumSortOrder,
    normalizeAlbumItems,
    fetchAlbum,
    deleteAlbumItem,
  }
}
