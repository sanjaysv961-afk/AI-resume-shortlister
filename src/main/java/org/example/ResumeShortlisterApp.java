package org.example;

import org.apache.tika.Tika;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@SpringBootApplication
@RestController
public class ResumeShortlisterApp {

    public static void main(String[] args) {
        SpringApplication.run(ResumeShortlisterApp.class, args);
    }

    // ==========================================
    // 1. FRONTEND: Served directly from Java
    // ==========================================
    @GetMapping(value = "/", produces = "text/html")
    public String getFrontend() {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>AI Resume Shortlister</title>
                <style>
                    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f4f7f6; padding: 40px; color: #333; }
                    .container { max-width: 900px; margin: auto; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
                    h1 { color: #2c3e50; text-align: center; }
                    label { font-weight: bold; display: block; margin-top: 20px; }
                    textarea, input { width: 100%; padding: 10px; margin-top: 5px; border: 1px solid #ccc; border-radius: 4px; box-sizing: border-box; }
                    button { background-color: #007bff; color: white; border: none; padding: 12px 20px; margin-top: 20px; border-radius: 4px; cursor: pointer; width: 100%; font-size: 16px; font-weight: bold; }
                    button:hover { background-color: #0056b3; }
                    #loading { display: none; text-align: center; margin-top: 20px; font-weight: bold; color: #007bff; }
                    .result-card { border-left: 5px solid #28a745; background: #f9f9f9; padding: 15px; margin-top: 15px; border-radius: 4px; }
                    .score { font-size: 24px; font-weight: bold; color: #28a745; }
                    .footer { text-align: center; margin-top: 40px; font-size: 13px; color: #888; border-top: 1px solid #eee; padding-top: 20px;}
                </style>
            </head>
            <body>
            
            <div class="container">
                <h1>AI Resume Shortlister</h1>
                
                <label for="jobDescription">Target Job Description:</label>
                <textarea id="jobDescription" rows="6" placeholder="Paste the full job requirements here..."></textarea>
            
                <label for="resumeFiles">Upload Candidate Resumes (PDF/DOCX):</label>
                <input type="file" id="resumeFiles" multiple accept=".pdf,.doc,.docx">
            
                <button onclick="analyzeResumes()">Shortlist Candidates</button>
            
                <div id="loading">Analyzing resumes with AI... This may take a moment.</div>
                <div id="resultsArea"></div>
                
                <div class="footer">Developed by SANJAY S V</div>
            </div>
            
            <script>
                async function analyzeResumes() {
                    const jobDesc = document.getElementById('jobDescription').value;
                    const fileInput = document.getElementById('resumeFiles');
                    const resultsArea = document.getElementById('resultsArea');
                    const loading = document.getElementById('loading');
            
                    if (!jobDesc || fileInput.files.length === 0) {
                        alert("Please provide both a job description and at least one resume.");
                        return;
                    }
            
                    resultsArea.innerHTML = '';
                    loading.style.display = 'block';
            
                    const formData = new FormData();
                    formData.append('jobDescription', jobDesc);
                    for (let i = 0; i < fileInput.files.length; i++) {
                        formData.append('files', fileInput.files[i]);
                    }
            
                    try {
                        const response = await fetch('/api/analyze', {
                            method: 'POST',
                            body: formData
                        });
            
                        const results = await response.json();
                        loading.style.display = 'none';
            
                        results.forEach((candidate, index) => {
                            const card = document.createElement('div');
                            card.className = 'result-card';
                            card.innerHTML = `
                                <h3 style="margin-top:0;">#${index + 1}: ${candidate.filename}</h3>
                                <div class="score">Match Score: ${candidate.score}%</div>
                                <p><strong>AI Reasoning:</strong> ${candidate.reasoning}</p>
                            `;
                            resultsArea.appendChild(card);
                        });
                    } catch (error) {
                        loading.style.display = 'none';
                        alert("An error occurred during analysis.");
                        console.error(error);
                    }
                }
            </script>
            </body>
            </html>
            """;
    }

    // ==========================================
    // 2. BACKEND: REST API & AI Engine
    // ==========================================
    @PostMapping("/api/analyze")
    public ResponseEntity<List<CandidateResult>> analyzeResumes(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("jobDescription") String jobDescription) {

        List<CandidateResult> results = new ArrayList<>();
        String apiKey = System.getenv("GEMINI_API_KEY"); // <-- PUT YOUR API KEY HERE

        for (MultipartFile file : files) {
            try (InputStream stream = file.getInputStream()) {
                // 1. Extract Text
                Tika tika = new Tika();
                String resumeText = tika.parseToString(stream);

                System.out.println("=== DEBUG: EXTRACTED TEXT ===");
                System.out.println(resumeText);

                // 2. Build API Request
                String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=" + apiKey;
                String prompt = "Act as an expert technical recruiter. Compare this resume to the job description. " +
                        "Return ONLY a JSON object with two keys: 'score' (integer 0-100) and 'reasoning' (2 sentences). " +
                        "Job: " + jobDescription + " | Resume: " + resumeText;

                String safePrompt = prompt.replace("\"", "\\\"").replace("\n", " ");
                String requestBody = "{ \"contents\": [{ \"parts\": [{\"text\": \"" + safePrompt + "\"}] }] }";

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                // 3. Call AI
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                System.out.println("=== DEBUG: RAW API RESPONSE ===");
                System.out.println(response.body());

                // 4. Parse AI Response
                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(response.body());

                String aiText;

                // Safely check if the "candidates" array exists and has at least one item
                if (rootNode.has("candidates") && rootNode.get("candidates").isArray() && rootNode.get("candidates").size() > 0) {
                    aiText = rootNode.get("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
                } else {
                    // Provide a valid JSON fallback so the mapper.readTree(aiText) below doesn't crash
                    aiText = "{ \"score\": 0, \"reasoning\": \"Failed to analyze. The document might be an unreadable image-based PDF or an API error occurred. Check backend logs.\" }";
                }

                // Clean markdown formatting if AI includes it (e.g. ```json ... ```)
                aiText = aiText.replaceAll("(?is)^```json\\s*", "").replaceAll("(?is)\\s*```$", "").trim();

                JsonNode resultNode = mapper.readTree(aiText);
                results.add(new CandidateResult(
                        file.getOriginalFilename(),
                        resultNode.path("score").asInt(),
                        resultNode.path("reasoning").asText()
                ));

            } catch (Exception e) {
                e.printStackTrace();
                results.add(new CandidateResult(file.getOriginalFilename(), 0, "Failed to analyze: " + e.getMessage()));
            }
        }

        // 5. Sort highest score to lowest
        results.sort(Comparator.comparingInt(CandidateResult::getScore).reversed());
        return ResponseEntity.ok(results);
    }

    // ==========================================
    // 3. DATA MODEL
    // ==========================================
    public static class CandidateResult {
        public String filename;
        public int score;
        public String reasoning;

        public CandidateResult(String filename, int score, String reasoning) {
            this.filename = filename;
            this.score = score;
            this.reasoning = reasoning;
        }

        public String getFilename() { return filename; }
        public int getScore() { return score; }
        public String getReasoning() { return reasoning; }
    }
}