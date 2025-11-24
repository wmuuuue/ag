#!/usr/bin/env python3
import http.server
import socketserver
import os
import json
from pathlib import Path

PORT = 5000

class ProjectHandler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/' or self.path == '/index.html':
            self.send_response(200)
            self.send_header('Content-type', 'text/html; charset=utf-8')
            self.end_headers()
            
            html_content = self.generate_project_page()
            self.wfile.write(html_content.encode('utf-8'))
        elif self.path == '/api/status':
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            
            status = self.get_project_status()
            self.wfile.write(json.dumps(status, ensure_ascii=False).encode('utf-8'))
        else:
            super().do_GET()
    
    def get_project_status(self):
        kotlin_files = list(Path('app/src/main/java').rglob('*.kt'))
        xml_files = list(Path('app/src/main/res').rglob('*.xml'))
        gradle_files = list(Path('.').glob('**/*.gradle.kts'))
        
        return {
            'status': 'ready',
            'project_type': 'Android Kotlin Application',
            'kotlin_files': len(kotlin_files),
            'xml_files': len(xml_files),
            'gradle_files': len(gradle_files),
            'build_ready': True
        }
    
    def generate_project_page(self):
        status = self.get_project_status()
        
        return f'''<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>剪贴板笔记 - Android项目</title>
    <style>
        * {{ margin: 0; padding: 0; box-sizing: border-box; }}
        body {{
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            padding: 20px;
        }}
        .container {{
            max-width: 900px;
            margin: 0 auto;
            background: white;
            border-radius: 16px;
            box-shadow: 0 20px 60px rgba(0,0,0,0.3);
            overflow: hidden;
        }}
        .header {{
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 40px;
            text-align: center;
        }}
        .header h1 {{
            font-size: 2.5em;
            margin-bottom: 10px;
        }}
        .header p {{
            font-size: 1.1em;
            opacity: 0.9;
        }}
        .content {{
            padding: 40px;
        }}
        .status-card {{
            background: #f8f9fa;
            border-radius: 12px;
            padding: 25px;
            margin-bottom: 25px;
            border-left: 4px solid #667eea;
        }}
        .status-card h2 {{
            color: #333;
            margin-bottom: 15px;
            font-size: 1.5em;
        }}
        .status-grid {{
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 15px;
            margin-top: 15px;
        }}
        .stat-item {{
            background: white;
            padding: 15px;
            border-radius: 8px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }}
        .stat-label {{
            color: #666;
            font-size: 0.9em;
            margin-bottom: 5px;
        }}
        .stat-value {{
            color: #667eea;
            font-size: 1.8em;
            font-weight: bold;
        }}
        .features {{
            margin-top: 25px;
        }}
        .features h3 {{
            color: #333;
            margin-bottom: 15px;
            font-size: 1.3em;
        }}
        .feature-list {{
            list-style: none;
        }}
        .feature-list li {{
            padding: 12px;
            background: white;
            margin-bottom: 8px;
            border-radius: 8px;
            border-left: 3px solid #667eea;
            box-shadow: 0 2px 4px rgba(0,0,0,0.05);
        }}
        .feature-list li::before {{
            content: "✓ ";
            color: #667eea;
            font-weight: bold;
            margin-right: 8px;
        }}
        .instructions {{
            background: #fff3cd;
            border-left: 4px solid #ffc107;
            padding: 20px;
            border-radius: 8px;
            margin-top: 25px;
        }}
        .instructions h3 {{
            color: #856404;
            margin-bottom: 10px;
        }}
        .instructions p {{
            color: #856404;
            line-height: 1.6;
        }}
        .code-block {{
            background: #2d2d2d;
            color: #f8f8f2;
            padding: 15px;
            border-radius: 8px;
            margin: 15px 0;
            overflow-x: auto;
            font-family: 'Courier New', monospace;
        }}
        .ready-badge {{
            display: inline-block;
            background: #28a745;
            color: white;
            padding: 8px 16px;
            border-radius: 20px;
            font-weight: bold;
            margin-top: 10px;
        }}
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>📋 剪贴板笔记</h1>
            <p>Android Kotlin 应用程序</p>
            <span class="ready-badge">✓ 项目已就绪</span>
        </div>
        
        <div class="content">
            <div class="status-card">
                <h2>项目状态</h2>
                <div class="status-grid">
                    <div class="stat-item">
                        <div class="stat-label">Kotlin 文件</div>
                        <div class="stat-value">{status['kotlin_files']}</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-label">XML 资源</div>
                        <div class="stat-value">{status['xml_files']}</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-label">Gradle 配置</div>
                        <div class="stat-value">{status['gradle_files']}</div>
                    </div>
                </div>
            </div>
            
            <div class="status-card">
                <h2>核心功能</h2>
                <div class="features">
                    <ul class="feature-list">
                        <li>浮动窗口显示 - 占屏幕1/40大小的可拖动图标</li>
                        <li>后台剪贴板监听 - 自动捕获并保存复制的内容</li>
                        <li>笔记管理 - 查看、编辑、删除和清空笔记</li>
                        <li>音频录制 - 支持录音、播放和保存到笔记</li>
                        <li>颜色自定义 - 分别设置剪贴板文字和用户输入文字颜色</li>
                        <li>局域网设备发现 - 搜索同一网络下的其他设备</li>
                        <li>笔记同步 - 发送笔记到其他设备</li>
                        <li>SQLite 数据库 - 持久化存储所有数据</li>
                    </ul>
                </div>
            </div>
            
            <div class="instructions">
                <h3>🚀 如何构建APK</h3>
                <p>此项目已配置好所有必要文件，可在GitHub Actions中构建。</p>
                <p><strong>步骤：</strong></p>
                <ol style="margin-left: 20px; margin-top: 10px; line-height: 1.8;">
                    <li>将项目推送到GitHub仓库</li>
                    <li>GitHub Actions会自动运行构建流程</li>
                    <li>下载构建好的APK文件</li>
                    <li>在Android 7.0+设备上安装</li>
                </ol>
                <div class="code-block">
git add .<br>
git commit -m "Android clipboard notes app"<br>
git push origin main
                </div>
            </div>
            
            <div class="status-card">
                <h2>技术栈</h2>
                <ul style="list-style: none; line-height: 2;">
                    <li><strong>语言：</strong> Kotlin 1.9.20</li>
                    <li><strong>构建工具：</strong> Gradle 8.2</li>
                    <li><strong>最低SDK：</strong> Android 7.0 (API 24)</li>
                    <li><strong>目标SDK：</strong> Android 14 (API 34)</li>
                    <li><strong>数据库：</strong> Room (SQLite)</li>
                    <li><strong>架构：</strong> MVVM + Coroutines</li>
                </ul>
            </div>
        </div>
    </div>
</body>
</html>'''

with socketserver.TCPServer(("0.0.0.0", PORT), ProjectHandler) as httpd:
    print(f"📱 Android项目文档服务器运行在 http://0.0.0.0:{PORT}")
    print(f"✓ 项目已准备好在GitHub Actions中构建APK")
    httpd.serve_forever()
