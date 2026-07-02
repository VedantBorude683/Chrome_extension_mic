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
    for (let i = 0; i < 15; i++) {
        const response = await fetch(`http://localhost:8080/api/v1/pr/report/${trackingId}`);

        if (response.status === 200) {
            const data = await response.json();

            // 1. If it's still pending, wait and loop again
            if (data.status === "PENDING") {
                console.log("⏳ Report still pending, waiting...");
            }
            // 2. If we got the actual report from the database!
            else if (data.aiAnalysis) {
                const raw = data.aiAnalysis;
                console.log("🔍 DEBUG: AI RAW RESPONSE:", raw);

                // Isolate the JSON from the AI string
                const start = raw.indexOf('{');
                const end = raw.lastIndexOf('}');

                if (start !== -1 && end !== -1) {
                    const jsonStr = raw.substring(start, end + 1);
                    try {
                        const parsedData = JSON.parse(jsonStr);
                        // Map it safely for the UI
                        return {
                            status: parsedData.status || "SAFE",
                            findings: parsedData.findings || ["No findings listed."]
                        };
                    } catch (e) {
                        console.error("🚨 JSON Parsing Error:", e);
                    }
                }
                // If we get here, AI didn't return valid JSON formatting, but we still caught it.
                return { status: "VULNERABLE", findings: ["AI returned malformed data: " + raw] };
            }
        }
        // Wait 5 seconds before checking the database again
        await new Promise(r => setTimeout(r, 5000));
    }
    throw new Error("Analysis failed or timed out.");
}

function injectReportIntoGitHubUI(report) {
    // 1. Inject Styles
    const styleId = 'br-premium-styles';
    if (!document.getElementById(styleId)) {
        const style = document.createElement("style");
        style.id = styleId;
        style.innerHTML = `
            @keyframes slideUpFade {
                from { opacity: 0; transform: translateY(20px); }
                to { opacity: 1; transform: translateY(0); }
            }
            .br-card {
                position: fixed; top: 80px; right: 20px; width: 320px; z-index: 9999;
                background: rgba(13, 17, 23, 0.7); backdrop-filter: blur(20px);
                border: 1px solid rgba(255,255,255,0.1); border-radius: 20px;
                padding: 20px; color: #ffffff; cursor: move;
                box-shadow: 0 10px 40px rgba(0,0,0,0.6);
                animation: slideUpFade 0.4s cubic-bezier(0.16, 1, 0.3, 1);
                transition: transform 0.2s ease, box-shadow 0.2s ease;
            }
            .br-card:hover { transform: translateY(-5px); box-shadow: 0 15px 50px rgba(0,0,0,0.8); }
            .status-pulse { animation: pulse 2s infinite; }
            @keyframes pulse {
                0% { box-shadow: 0 0 0 0 rgba(207, 34, 46, 0.7); }
                70% { box-shadow: 0 0 0 10px rgba(207, 34, 46, 0); }
                100% { box-shadow: 0 0 0 0 rgba(207, 34, 46, 0); }
            }
        `;
        document.head.appendChild(style);
    }

    // 2. Build Card
    const card = document.createElement("div");
    card.className = "br-card " + (report.status === "VULNERABLE" ? "status-pulse" : "");
    if (report.status === "VULNERABLE") card.style.borderColor = "#cf222e";

    const statusColor = report.status === "VULNERABLE" ? "#ff7b72" : "#3fb950";

    card.innerHTML = `
        <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 15px;">
            <img src="https://assets.streamlinehq.com/image/private/w_300,h_300,ar_1/f_auto/v1/icons/all-icons/shield-qepmdc10jsro72qlofy88d.png/shield-a0yea58cq0hotm9wdyc7v.png" 
                 style="width: 35px; height: 35px; filter: drop-shadow(0 0 5px ${statusColor});">
            <h3 style="margin:0; font-size: 18px; font-weight: 600;">Blast Radius</h3>
        </div>
        <div style="font-size: 14px; margin-bottom: 15px;">
            Status: <span style="color: ${statusColor}; font-weight: bold;">${report.status}</span>
        </div>
        <div style="border-top: 1px solid rgba(255,255,255,0.1); padding-top: 10px;">
            <p style="font-size: 12px; opacity: 0.7; margin: 0 0 5px 0;">Findings:</p>
            <ul id="findings-list" style="padding-left: 20px; font-size: 12px; margin: 0;"></ul>
        </div>
    `;

    const list = card.querySelector("#findings-list");
    report.findings.forEach(f => {
        const li = document.createElement("li");
        li.textContent = f;
        list.appendChild(li);
    });

    document.body.appendChild(card);
    card.onmousedown = (e) => {
        // Calculate initial mouse offset
        let rect = card.getBoundingClientRect();
        let shiftX = e.clientX - rect.left;
        let shiftY = e.clientY - rect.top;

        // Lock the current absolute position so it doesn't snap
        card.style.right = 'auto';
        card.style.bottom = 'auto';
        card.style.left = rect.left + 'px';
        card.style.top = rect.top + 'px';

        function moveAt(pageX, pageY) {
            card.style.left = (pageX - shiftX) + 'px';
            card.style.top = (pageY - shiftY) + 'px';
        }

        function onMouseMove(e) {
            moveAt(e.pageX, e.pageY);
        }

        document.addEventListener('mousemove', onMouseMove);

        document.onmouseup = () => {
            document.removeEventListener('mousemove', onMouseMove);
            document.onmouseup = null;
        };
    };
}