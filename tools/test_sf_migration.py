"""Check the production 1->2 SQL against Room's exported schemas without Android dependencies."""
import json
import re
import sqlite3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
schemas = root / "app/schemas/com.novelai.assistant.data.db.NovelDatabase"
old = json.loads((schemas / "1.json").read_text(encoding="utf-8"))["database"]
new = json.loads((schemas / "2.json").read_text(encoding="utf-8"))["database"]
db = sqlite3.connect(":memory:")
for entity in old["entities"]:
    db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
db.execute("INSERT INTO books VALUES ('original', '本地旧书', '作者', NULL, 'old.txt', 'LOCAL_TXT', 2, 1, 50, '收藏', 100, 200)")
db.execute("INSERT INTO chapters VALUES ('old-ch', 'original', 0, '第一章', '保留的正文', 'ORIGINAL', NULL, NULL, 1, 100)")
db.execute("INSERT INTO chapters VALUES ('failed-ch', 'original', 1, '第二章', '（本章抓取失败：未登录）', 'ORIGINAL', NULL, NULL, 0, 100)")
source = (root / "app/src/main/java/com/novelai/assistant/data/db/NovelDatabase.kt").read_text(encoding="utf-8")
statements = re.findall(r'db\.execSQL\("([^"\n]+)"\)', source)
assert len(statements) == 5, "Review changed migration statements"
with db:
    for sql in statements:
        db.execute(sql)
expected = sqlite3.connect(":memory:")
for entity in new["entities"]:
    table = entity["tableName"]
    expected.execute(entity["createSql"].replace("${TABLE_NAME}", table))
    assert db.execute(f"PRAGMA table_info({table})").fetchall() == expected.execute(f"PRAGMA table_info({table})").fetchall(), table
assert db.execute("SELECT content,isFavorite,remoteId,downloaded FROM chapters WHERE id='old-ch'").fetchone() == ("保留的正文", 1, None, 0)
assert db.execute("SELECT currentReadingChapterIndex,readingProgressPercent,category FROM books").fetchone() == (1, 50, "收藏")
assert db.execute("SELECT content,fetchError FROM chapters WHERE id='failed-ch'").fetchone() == ("", "旧版抓取失败，请重新获取本书目录")
db.execute("INSERT INTO chapters VALUES ('cached', 'original', 2, '缓存章', '临时正文', 'ORIGINAL', NULL, NULL, 0, 100, '123', 0, 0, '')")
db.execute("INSERT INTO chapters VALUES ('pinned', 'original', 3, '下载章', '下载正文', 'ORIGINAL', NULL, NULL, 0, 100, '124', 0, 1, '')")
dao = (root / "app/src/main/java/com/novelai/assistant/data/db/Daos.kt").read_text(encoding="utf-8")
clear = re.search(r'@Query\("([^"\n]+)"\)\s+suspend fun clearReadingCache', dao).group(1)
db.execute(clear)
assert db.execute("SELECT content FROM chapters WHERE id='cached'").fetchone()[0] == ""
assert db.execute("SELECT content FROM chapters WHERE id='pinned'").fetchone()[0] == "下载正文"
assert db.execute("SELECT content FROM chapters WHERE id='old-ch'").fetchone()[0] == "保留的正文"
print("PASS: Room 1->2 schema, retained book/progress/favorite, failed-text cleanup, cache clear preserves downloads and local books")
