const messageInput = document.getElementById('message-input');
const sendButton = document.getElementById('send-button');
const chatMessages = document.getElementById('chat-messages');
const historyList = document.getElementById('history-list');
const newChatButton = document.getElementById('new-chat-button');

let currentChatId = null;
let thinkingIndicatorElement = null; 

// --- Event Listeners ---
newChatButton.addEventListener('click', startNewChat);
sendButton.addEventListener('click', sendMessage);
messageInput.addEventListener('keypress', function (event) {
    // Allow Shift+Enter for new line, send only on Enter without Shift
    if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault(); // Prevent default Enter behavior (like adding a newline)
        sendMessage();
    }
});

// --- Chat Management Functions ---

function generateChatId() {
    return Date.now().toString();
}

function startNewChat() {
    currentChatId = generateChatId();
    chatMessages.innerHTML = ''; // Clear message display area
    messageInput.value = '';     // Clear input field
    hideThinkingIndicator();     // Ensure indicator is hidden if a new chat starts abruptly
    // Display initial bot messages for the new chat
    displayInitialBotMessages(currentChatId);
    // Save this new (empty for now) chat to the list immediately
    saveChatList();
    // Reload the list to show the new chat entry
    loadChatList();
    // Ensure the new chat is marked as active visually
    setActiveChatItem(currentChatId);
    console.log('New chat started with ID:', currentChatId);
}

function sendMessage() {
    const messageText = messageInput.value.trim();
    if (!messageText || !currentChatId) {
        return;
    }

    displayMessage(messageText, 'user', currentChatId);
    saveMessageToLocalStorage(currentChatId, 'user', messageText);
    messageInput.value = ''; // Clear input after sending

    // ++ Show thinking indicator ++
    showThinkingIndicator();

    const apiUrl = 'http://localhost:8080/api/chat'; // MAKE SURE your backend server is running here

    fetch(apiUrl, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({ message: messageText, chatId: currentChatId }), // Optionally send chatId if backend needs it
    })
    .then(response => {
        if (!response.ok) {
            return response.text().then(text => {
                throw new Error(`HTTP error! status: ${response.status}, message: ${text || 'No error details provided'}`);
            });
        }
        return response.json();
    })
    .then(data => {
        // ++ Hide thinking indicator ++
        hideThinkingIndicator();

        const botResponse = data.response || "Sorry, I didn't get a valid response.";
        displayMessage(botResponse, 'bot', currentChatId);
        saveMessageToLocalStorage(currentChatId, 'bot', botResponse);
    })
    .catch(error => {
        console.error('Error sending message to backend:', error);
        // ++ Hide thinking indicator ++
        hideThinkingIndicator();

        // Display a user-friendly error message in the chat
        const errorMessage = `Error communicating with bot: ${error.message}`;
        displayMessage(errorMessage, 'bot', currentChatId); // Display error as a bot message
        // Optionally save error message to history, marked appropriately if needed
        // saveMessageToLocalStorage(currentChatId, 'error', errorMessage);
    });
}


// --- Display and Storage Functions ---

// ++ New Function: Show Thinking Indicator ++
function showThinkingIndicator() {
    // Remove any existing indicator first
    hideThinkingIndicator();

    // Create the indicator elements
    const messageContainer = document.createElement('div');
    messageContainer.classList.add('message-container', 'bot-message-container', 'thinking-indicator'); // Add specific class

    const messageDiv = document.createElement('div');
    messageDiv.classList.add('message', 'bot-message'); // Style like a bot message
    messageDiv.textContent = 'Thinking...'; // Indicator text

    messageContainer.appendChild(messageDiv);

    // Append to chat and store the reference
    chatMessages.appendChild(messageContainer);
    thinkingIndicatorElement = messageContainer; // Store reference for removal

    // Scroll to bottom smoothly to ensure indicator is visible
    chatMessages.scrollTo({ top: chatMessages.scrollHeight, behavior: 'smooth' });
}

// ++ New Function: Hide Thinking Indicator ++
function hideThinkingIndicator() {
    if (thinkingIndicatorElement && thinkingIndicatorElement.parentNode === chatMessages) {
        chatMessages.removeChild(thinkingIndicatorElement);
        thinkingIndicatorElement = null; // Clear the reference
    }
}


function displayMessage(message, sender, chatId) {
    if (chatId !== currentChatId) {
        console.warn(`Attempted to display message for chat ${chatId} while current chat is ${currentChatId}. Skipping.`);
        return;
    }

    const messageContainer = document.createElement('div');
    messageContainer.classList.add('message-container');
    messageContainer.dataset.sender = sender;

    if (sender === 'user') {
        messageContainer.classList.add('user-message-container');
    } else {
        messageContainer.classList.add('bot-message-container');
    }

    const messageDiv = document.createElement('div');
    messageDiv.classList.add('message');
    if (sender === 'user') {
        messageDiv.classList.add('user-message');
    } else if (sender === 'bot') {
        messageDiv.classList.add('bot-message');
    }

    // Use textContent for the main message to prevent XSS
    messageDiv.textContent = message;

    // Timestamp logic remains the same
    const timestamp = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    const timeSpan = document.createElement('span');
    timeSpan.classList.add('message-time');
    timeSpan.textContent = timestamp;

    // Append time span directly after the text content within the message div
    messageDiv.appendChild(timeSpan);


    messageContainer.appendChild(messageDiv);
    chatMessages.appendChild(messageContainer);

    // Scroll to bottom smoothly
    chatMessages.scrollTo({ top: chatMessages.scrollHeight, behavior: 'smooth' });
}

function saveMessageToLocalStorage(chatId, sender, text) {
    if (!chatId) return;
    const chatHistory = getChatHistory(chatId);
    chatHistory.push({ sender, text, timestamp: new Date().toISOString() });
    localStorage.setItem(`chat_${chatId}`, JSON.stringify(chatHistory));

    // Update the preview text only if it was the user's message
    // (or maybe the first bot message) to avoid overwriting with short bot responses.
    // You might want more sophisticated logic here.
    if (sender === 'user') {
       updateChatListEntry(chatId, text);
    } else if (getChatHistory(chatId).length === 1) { // If it's the very first message (initial bot greeting)
       updateChatListEntry(chatId, text);
    }
}

function getChatHistory(chatId) {
    if (!chatId) return [];
    const storedHistory = localStorage.getItem(`chat_${chatId}`);
    try {
        return storedHistory ? JSON.parse(storedHistory) : [];
    } catch (error) {
        console.error(`Error parsing chat history for chat_${chatId}:`, error);
        return [];
    }
}

function loadChatMessages(chatId) {
    if (!chatId) {
        console.warn("loadChatMessages called with null or undefined chatId.");
        return;
    }
    console.log(`Loading messages for chat ID: ${chatId}`);

    currentChatId = chatId;
    chatMessages.innerHTML = ''; // Clear previous messages
    hideThinkingIndicator();     // Ensure indicator is hidden when switching chats
    setActiveChatItem(chatId);   // Highlight the loaded chat in the sidebar

    const history = getChatHistory(chatId);
    console.log(`Found ${history.length} messages in history for chat ${chatId}`);

    if (history.length === 0) {
        console.log(`No messages found for chat ${chatId}, displaying initial prompt.`);
        displayInitialBotMessages(chatId);
    } else {
        history.forEach(msg => {
            // Recreate timestamp display logic here if needed from stored timestamp
            // For now, displayMessage uses current time, which is simpler
            displayMessage(msg.text, msg.sender, chatId);
        });
    }

    chatMessages.scrollTop = chatMessages.scrollHeight;
}


function saveChatList() {
    let chatList = JSON.parse(localStorage.getItem('chatList')) || [];
    if (currentChatId && !chatList.includes(currentChatId)) {
        chatList.unshift(currentChatId);
        localStorage.setItem('chatList', JSON.stringify(chatList));
        console.log('Chat list saved:', chatList);
    }
}

function loadChatList() {
    historyList.innerHTML = ''; // Clear the existing list
    let chatList = JSON.parse(localStorage.getItem('chatList')) || [];
    console.log('Loading chat list:', chatList);

    chatList = chatList.filter(id => id !== null && id !== undefined);

    if (chatList.length === 0) {
        console.log("Chat list empty, starting new chat.");
        startNewChat();
        return;
    }

    chatList.forEach(chatId => {
        const listItem = document.createElement('li');
        listItem.dataset.chatId = chatId;

        const titleSpan = document.createElement('span');
        titleSpan.classList.add('chat-title');
        titleSpan.textContent = getChatPreviewText(chatId);

        const deleteButton = document.createElement('button');
        deleteButton.classList.add('delete-chat-button');
        deleteButton.textContent = '✕';
        deleteButton.title = 'Delete chat';

        listItem.appendChild(titleSpan);
        listItem.appendChild(deleteButton);

        listItem.addEventListener('click', (event) => {
            if (event.target !== deleteButton) {
                 if (currentChatId !== chatId) {
                    loadChatMessages(chatId);
                 }
            }
        });

        deleteButton.addEventListener('click', (event) => {
            event.stopPropagation();
            deleteChat(chatId);
        });

        historyList.appendChild(listItem);
    });

    let chatToLoad = null;
    if (currentChatId && chatList.includes(currentChatId)) {
        chatToLoad = currentChatId;
    } else if (chatList.length > 0) {
        chatToLoad = chatList[0];
    }

    if (chatToLoad) {
        // Load only if necessary (different chat or empty display)
        if (currentChatId !== chatToLoad || chatMessages.children.length === 0) {
             loadChatMessages(chatToLoad);
        } else {
             setActiveChatItem(currentChatId); // Ensure highlight is correct
        }
    } else {
        console.warn("No valid chat to load, starting new chat.");
        startNewChat();
    }
}

function deleteChat(chatIdToDelete) {
    console.log('Attempting to delete chat ID:', chatIdToDelete);
    if (!confirm(`Are you sure you want to delete this chat?`)) {
        return;
    }

    localStorage.removeItem(`chat_${chatIdToDelete}`);
    console.log(`Removed messages for chat_${chatIdToDelete}`);

    let chatList = JSON.parse(localStorage.getItem('chatList')) || [];
    const updatedChatList = chatList.filter(id => id !== chatIdToDelete);
    localStorage.setItem('chatList', JSON.stringify(updatedChatList));
    console.log('Updated chat list:', updatedChatList);

    if (currentChatId === chatIdToDelete) {
        currentChatId = null;
        chatMessages.innerHTML = '';
        messageInput.value = '';
        hideThinkingIndicator(); // Hide indicator if active chat is deleted

        if (updatedChatList.length > 0) {
            console.log("Deleted active chat, loading next chat:", updatedChatList[0]);
            // Delay slightly to allow UI to clear before loading new chat
            setTimeout(() => loadChatMessages(updatedChatList[0]), 50);
        } else {
            console.log("Deleted last chat, starting new chat.");
            setTimeout(startNewChat, 50); // Delay slightly
        }
    } else {
        // Just remove the item from the sidebar UI
        const itemToRemove = historyList.querySelector(`li[data-chat-id='${chatIdToDelete}']`);
        if (itemToRemove) {
            historyList.removeChild(itemToRemove);
            console.log("Removed deleted chat item from sidebar.");
        }
         // No need to reload messages if a non-active chat was deleted
    }
}

function getChatPreviewText(chatId) {
    const history = getChatHistory(chatId);
    let preview = `New Chat`; // Simpler default

    if (history.length > 0) {
        const firstUserMessage = history.find(msg => msg.sender === 'user');
        if (firstUserMessage?.text) {
            preview = firstUserMessage.text.substring(0, 25).trim() + (firstUserMessage.text.length > 25 ? '...' : '');
        } else {
            // If no user message, use the first message (likely initial bot greeting)
             const firstMessage = history[0];
             if (firstMessage?.text) {
                 preview = firstMessage.text.substring(0, 25).trim() + (firstMessage.text.length > 25 ? '...' : '');
             } else {
                 // Fallback to timestamp if text is somehow empty
                 preview = `Chat ${new Date(parseInt(chatId)).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
             }
        }
    } else {
        // Add time for clarity on empty chats
        preview += ` (${new Date(parseInt(chatId)).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })})`;
    }
    return preview;
}

function updateChatListEntry(chatId, latestText) {
    const listItem = historyList.querySelector(`li[data-chat-id='${chatId}']`);
    if (listItem) {
        const titleSpan = listItem.querySelector('.chat-title');
        if (titleSpan) {
            // Always update with the latest user text for relevance
             titleSpan.textContent = latestText.substring(0, 25).trim() + (latestText.length > 25 ? '...' : '');
        }
         // Move updated chat to the top of the list visually
         if (historyList.firstChild !== listItem) {
             historyList.insertBefore(listItem, historyList.firstChild);
         }
    }
}

function setActiveChatItem(chatId) {
    const items = historyList.querySelectorAll('li');
    items.forEach(item => {
        if (item.dataset.chatId === chatId) {
            item.style.backgroundColor = '#2a2a2a'; // Slightly different active style
            item.style.fontWeight = 'bold';
        } else {
            item.style.backgroundColor = '';
            item.style.fontWeight = 'normal';
        }
    });
}

function displayInitialBotMessages(chatId) {
    // This function should ONLY add the message if the history is truly empty.
    // It avoids adding duplicates when loadChatMessages calls it for an existing empty chat.
    const existingHistory = getChatHistory(chatId);
    if (existingHistory.length === 0) {
        const initialMessageText = 'Hello! How can I help you today?';
        displayMessage(initialMessageText, 'bot', chatId);
        // Save the initial message ONLY if it was just displayed
        saveMessageToLocalStorage(chatId, 'bot', initialMessageText);
    }
}

// --- Initial Load ---
loadChatList(); // Load history and potentially the last active chat on startup