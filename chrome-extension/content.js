console.log("🚀 Blast Radius Extension: Infiltrated GitHub successfully!");

const prUrl = window.location.href;

if (prUrl.includes("/pull/") && !prUrl.includes("/files") && !prUrl.includes("/commits")) {
    console.log("🎯 Valid PR page detected. Initiating pipeline...");

    const urlParts = window.location.pathname.split('/');
    const repositoryName = `${urlParts[1]}/${urlParts[2]}`;
    const diffUrl = prUrl + ".diff";

    (async () => {
        try {
            // 1. Fetch Diff
            const diffResponse = await chrome.runtime.sendMessage({ action: "fetchDiff", url: diffUrl });
            if (!diffResponse.success) throw new Error(diffResponse.error);

            // 2. Dispatch to Backend
            const response = await fetch("http://localhost:8080/api/v1/pr/analyze", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ repositoryName, codeDiff: diffResponse.data })
            });
            const { trackingId } = await response.json();
            console.log("✅ Pipeline started. Tracking ID:", trackingId);

            // 3. Poll for report (Robust loop)
            const report = await pollForReport(trackingId);

            // 4. Inject Premium UI
            injectReportIntoGitHubUI(report);

        } catch (error) {
            console.error("❌ Pipeline Failed:", error);
        }
    })();
}

async function pollForReport(trackingId) {
    const MAX_ATTEMPTS = 60; // 5 full minutes of patience for the local LLM

    for (let i = 0; i < MAX_ATTEMPTS; i++) {
        try {
            const response = await fetch(`http://localhost:8080/api/v1/pr/report/${trackingId}`);

            if (response.status === 200) {
                const data = await response.json();

                // 1. If it's still pending, wait and loop again
                if (data.status === "PENDING") {
                    console.log(`⏳ Report still pending (Attempt ${i + 1}/${MAX_ATTEMPTS}), waiting...`);
                }
                // 2. If we got the actual report from the database!
                else if (data.aiAnalysis) {
                    const raw = data.aiAnalysis;
                    console.log("🔍 DEBUG: AI RAW RESPONSE:", raw);

                    // Isolate the JSON from the AI string (Double-Shield Defense)
                    const start = raw.indexOf('{');
                    const end = raw.lastIndexOf('}');

                    if (start !== -1 && end !== -1) {
                        const jsonStr = raw.substring(start, end + 1);
                        try {
                            const parsedData = JSON.parse(jsonStr);
                            return {
                                status: parsedData.status || "SAFE",
                                findings: parsedData.findings || ["No findings listed."],
                                affectedServices: data.affectedServices || []
                            };
                        } catch (e) {
                            console.error("🚨 JSON Parsing Error:", e);
                        }
                    }
                    // Fallback if AI completely bombed the formatting
                    return { status: "VULNERABLE", findings: ["AI returned malformed data: " + raw] };
                }
            }
        } catch (fetchError) {
            console.warn("⚠️ Fetch error during polling, retrying...", fetchError);
        }

        // Wait 5 seconds before checking the database again
        await new Promise(r => setTimeout(r, 5000));
    }
    throw new Error("Analysis failed or timed out. Backend took too long.");
}

// Helper: Generates the animated SVG Mini-Map
function generateMiniMapSVG(status, services) {
    const isVulnerable = status === "VULNERABLE";
    const color = isVulnerable ? "#f85149" : "#2ea043";
    const centerColor = isVulnerable ? "rgba(248, 81, 73, 0.9)" : "rgba(46, 160, 67, 0.9)";
    const pulseAnim = isVulnerable ? `<animate attributeName="opacity" values="0.5;1;0.5" dur="1.5s" repeatCount="indefinite"/>` : "";

    // If no dependencies, just show the PR node
    if (!services || services.length === 0) services = ["No Dependencies"];

    let nodesHtml = '';
    let linesHtml = '';

    // Dynamically calculate X coordinates based on how many real services there are
    services.forEach((serviceName, index) => {
        const spacing = 280 / (services.length + 1);
        const xCenter = spacing * (index + 1);

        // Draw Animated Line
        linesHtml += `<line x1="140" y1="70" x2="${xCenter}" y2="35" stroke="${color}" stroke-width="2" marker-end="url(#arrow)" stroke-dasharray="4" style="animation: dash 20s linear infinite;" />`;

        // Draw Service Node
        nodesHtml += `
            <rect x="${xCenter - 40}" y="15" width="80" height="26" rx="4" fill="rgba(48, 54, 61, 0.9)" border="1px solid rgba(255,255,255,0.1)"/>
            <text x="${xCenter}" y="32" fill="#c9d1d9" font-size="9" text-anchor="middle" font-weight="600">${serviceName.substring(0, 15)}</text>
        `;
    });

    return `
        <svg viewBox="0 0 280 120" style="width: 100%; height: auto; border-radius: 8px; background: rgba(0,0,0,0.2); margin-bottom: 15px; border: 1px solid rgba(255,255,255,0.05);">
            <defs>
                <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
                    <path d="M 0 0 L 10 5 L 0 10 z" fill="${color}" />
                </marker>
            </defs>

            ${linesHtml}
            ${nodesHtml}

            <rect x="90" y="60" width="100" height="30" rx="6" fill="${centerColor}">
                ${pulseAnim}
            </rect>
            <text x="140" y="79" fill="#ffffff" font-size="11" text-anchor="middle" font-weight="bold">This PR</text>
            
            <style>
                @keyframes dash { to { stroke-dashoffset: -100; } }
            </style>
        </svg>
    `;
}

function injectReportIntoGitHubUI(report) {
    // 1. Inject Premium Styles
    const styleId = 'br-premium-styles';
    if (!document.getElementById(styleId)) {
        const style = document.createElement("style");
        style.id = styleId;
        style.innerHTML = `
            @keyframes slideUpFade {
                from { opacity: 0; transform: translateY(20px); }
                to { opacity: 1; transform: translateY(0); }
            }
            @keyframes slideDownFadeOut {
                from { opacity: 1; transform: translateY(0); }
                to { opacity: 0; transform: translateY(20px); }
            }
            .br-card {
                position: fixed; top: 80px; right: 20px; width: 320px; z-index: 99999;
                background: rgba(13, 17, 23, 0.7); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px);
                border: 1px solid rgba(255,255,255,0.1); border-radius: 16px;
                padding: 20px; color: #ffffff;
                box-shadow: 0 10px 40px rgba(0,0,0,0.6);
                animation: slideUpFade 0.4s cubic-bezier(0.16, 1, 0.3, 1);
                transition: box-shadow 0.2s ease;
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
            }
            .br-card:hover { box-shadow: 0 15px 50px rgba(0,0,0,0.8); }
            .br-header-area { cursor: grab; display: flex; align-items: center; justify-content: space-between; margin-bottom: 15px; }
            .br-header-area:active { cursor: grabbing; }
            .br-close-btn {
                background: none; border: none; color: rgba(255,255,255,0.5); font-size: 18px; 
                cursor: pointer; padding: 0; line-height: 1; transition: color 0.2s;
            }
            .br-close-btn:hover { color: #ffffff; }
            .status-pulse { animation: pulse 2s infinite; }
            @keyframes pulse {
                0% { box-shadow: 0 0 0 0 rgba(207, 34, 46, 0.5); }
                70% { box-shadow: 0 0 0 10px rgba(207, 34, 46, 0); }
                100% { box-shadow: 0 0 0 0 rgba(207, 34, 46, 0); }
            }
        `;
        document.head.appendChild(style);
    }

    // 2. Build Card
    const card = document.createElement("div");
    card.className = "br-card " + (report.status === "VULNERABLE" ? "status-pulse" : "");
    if (report.status === "VULNERABLE") card.style.borderColor = "rgba(207, 34, 46, 0.5)";

    const statusColor = report.status === "VULNERABLE" ? "#ff7b72" : "#3fb950";

    card.innerHTML = `
        <div class="br-header-area" id="br-drag-handle">
            <div style="display: flex; align-items: center; gap: 12px; pointer-events: none;">
                <img src="https://assets.streamlinehq.com/image/private/w_300,h_300,ar_1/f_auto/v1/icons/all-icons/shield-qepmdc10jsro72qlofy88d.png/shield-a0yea58cq0hotm9wdyc7v.png" 
                     style="width: 28px; height: 28px; filter: drop-shadow(0 0 5px ${statusColor});">
                <h3 style="margin:0; font-size: 16px; font-weight: 600; letter-spacing: 0.5px;">Blast Radius</h3>
            </div>
            <button class="br-close-btn" id="br-close-btn">&times;</button>
        </div>
        <div style="font-size: 14px; margin-bottom: 12px;">
            Status: <span style="color: ${statusColor}; font-weight: bold; letter-spacing: 0.5px;">${report.status}</span>
        </div>
        
        ${generateMiniMapSVG(report.status, report.affectedServices)}

        <div style="border-top: 1px solid rgba(255,255,255,0.1); padding-top: 12px;">
            <p style="font-size: 12px; opacity: 0.7; margin: 0 0 8px 0; text-transform: uppercase; font-weight: 600;">Findings</p>
            <ul id="findings-list" style="padding-left: 20px; font-size: 13px; margin: 0; line-height: 1.5; opacity: 0.9;"></ul>
        </div>
    `;

    // 3. Append Findings
    const list = card.querySelector("#findings-list");
    report.findings.forEach(f => {
        const li = document.createElement("li");
        li.style.marginBottom = "6px";
        li.textContent = f;
        list.appendChild(li);
    });

    document.body.appendChild(card);

    // 4. Smooth Close Logic
    const closeBtn = card.querySelector('#br-close-btn');
    closeBtn.onclick = () => {
        card.style.animation = "slideDownFadeOut 0.3s cubic-bezier(0.16, 1, 0.3, 1) forwards";
        setTimeout(() => card.remove(), 300);
    };

    // 5. Scroll-Proof Dragging Logic
    const dragHandle = card.querySelector('#br-drag-handle');
    dragHandle.onmousedown = (e) => {
        if (e.target === closeBtn) return; // Don't drag if clicking the close button

        e.preventDefault(); // Prevent text highlighting while dragging

        let shiftX = e.clientX - card.getBoundingClientRect().left;
        let shiftY = e.clientY - card.getBoundingClientRect().top;

        function moveAt(clientX, clientY) {
            // Use clientX/Y so it stays fixed relative to the screen, even if scrolled
            card.style.left = (clientX - shiftX) + 'px';
            card.style.top = (clientY - shiftY) + 'px';
            card.style.right = 'auto'; // Clear right pinning
        }

        function onMouseMove(e) {
            moveAt(e.clientX, e.clientY);
        }

        document.addEventListener('mousemove', onMouseMove);

        document.onmouseup = () => {
            document.removeEventListener('mousemove', onMouseMove);
            document.onmouseup = null;
        };
    };
}