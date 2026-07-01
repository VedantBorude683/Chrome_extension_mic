chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
    if (request.action === "fetchDiff") {
        fetch(request.url)
            .then(response => response.text())
            .then(text => sendResponse({ success: true, data: text }))
            .catch(error => sendResponse({ success: false, error: error.message }));

        // Return true to indicate we will send the response asynchronously
        return true;
    }
});