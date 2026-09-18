"""Build an allowlisted static demo; never copy server configs or local secrets."""
from pathlib import Path
import shutil
root = Path(__file__).resolve().parents[1]
source = root / 'src/main/resources/static'
out = root / 'target/pages-demo'
out.mkdir(parents=True, exist_ok=True)
for name in ('app.js', 'styles.css'):
    shutil.copyfile(source / name, out / name)
shutil.copytree(source / 'vendor', out / 'vendor', dirs_exist_ok=True)
shutil.copyfile(root / 'demo/demo.js', out / 'demo.js')
html = (source / 'index.html').read_text()
html = html.replace('href="/', 'href="./').replace('src="/', 'src="./')
html = html.replace('<script defer src="./app.js">', '<script defer src="./demo.js"></script>\n    <script defer src="./app.js">')
html = html.replace('<html lang="zh-CN">', '<html lang="zh-CN" data-demo="true">')
html = html.replace('你的数据分析伙伴', '交互演示 · 无需 API Key')
html = html.replace('用自然语言探索业务数据。让 Qiqi\n              帮你查询、对比，发现变化背后的线索。', '体验逐字报告、历史对话和主题切换。所有回答均为预设示例，不连接真实模型或数据库。')
html = html.replace('✓ 真实数据查询', '✓ 模拟流式报告').replace('✓ 按权限访问', '✓ 本地对话记录').replace('✓ SQL 证据可追溯', '✓ 双主题切换')
html = html.replace('销售示例数据库', '模拟数据集').replace('5 张业务表', '静态演示')
html = html.replace('<title>Qiqi DataAgent · 数据分析工作台</title>', '<title>Qiqi DataAgent · 交互演示</title>')
(out / 'index.html').write_text(html)
(out / '.nojekyll').write_text('')
print(out)
