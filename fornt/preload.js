const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
    newChat: () => ipcRenderer.invoke('new-chat'),
    getChatHistory: () => ipcRenderer.invoke('get-chat-history'),
    getChatMessages: (chatId) => ipcRenderer.invoke('get-chat-messages', chatId),
});