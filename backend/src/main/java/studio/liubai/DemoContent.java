package studio.liubai;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DemoContent implements CommandLineRunner {
  final NoteStore store;
  final org.springframework.jdbc.core.JdbcTemplate db;
  final boolean enabled;

  public DemoContent(
      NoteStore store,
      org.springframework.jdbc.core.JdbcTemplate db,
      @Value("${SEED_DEMO:true}") boolean enabled) {
    this.store = store;
    this.db = db;
    this.enabled = enabled;
  }

  @org.springframework.transaction.annotation.Transactional
  public void run(String... args) {
    if (!enabled
        || db.queryForObject(
                "SELECT COUNT(*) FROM app_meta WHERE meta_key='demo-seeded'", Integer.class)
            > 0) return;
    if (db.queryForObject("SELECT COUNT(*) FROM notes", Integer.class) > 0) {
      db.update("INSERT INTO app_meta VALUES('demo-seeded','true')");
      return;
    }
    add(
        "把知识连成一片森林",
        "knowledge",
        "知识管理",
        List.of("知识体系", "学习方法"),
        true,
        1,
        "知识的价值，不在于收藏了多少，而在于建立了多少连接。\n\n"
            + "## 从收集到理解\n\n"
            + "每次读完一篇文章，试着用自己的语言写下三句话：它说了什么，我为什么认同，它和已知的什么有关。\n\n"
            + "> 让笔记成为思考的起点，而不是信息的终点。\n\n"
            + "## 建立自己的连接\n\n"
            + "- 给新概念找到一个熟悉的例子\n"
            + "- 用标签连接不同领域\n"
            + "- 定期回来，给旧想法添加新的注解\n\n"
            + "## 一个小小的实践\n\n"
            + "选择本周读过的一篇文章，不看原文，写下你还记得的内容。那些留下来的东西，才是属于你的知识。");
    add(
        "慢一点，也是一种前进",
        "thoughts",
        "日常思考",
        List.of("自我成长", "生活方式"),
        true,
        0,
        "最近在练习一件事：不急着填满所有的空隙。\n\n"
            + "散步时不听播客，吃饭时放下手机，读书时允许自己停在某一页。原来那些看似没有产出的时间，也在悄悄滋养着我们。\n\n"
            + "## 关于留白\n\n"
            + "> 生活需要一些没有安排的下午，想法需要一些不被打扰的空间。\n\n"
            + "不是每一次停顿都需要被解释。有时候，留白本身就是答案。\n\n"
            + "## 给自己的提醒\n\n"
            + "- 留出一段不看屏幕的时间\n"
            + "- 去一条没走过的路散步\n"
            + "- 读一本与工作无关的书");
    add(
        "周末，去山里找一点绿色",
        "diary",
        "生活碎片",
        List.of("周末", "自然"),
        true,
        2,
        "![山间的晨光](/landscape.svg)\n\n"
            + "城市的声音渐渐远去，耳边只剩下风穿过树梢的声音。\n\n"
            + "沿着山路慢慢走，遇见一片很漂亮的绿色。没有目的地，也不赶时间。\n\n"
            + "> 把日子过成自己喜欢的样子，从一个普通的周末开始。\n\n"
            + "午后找了一家小店，点了一杯热茶。窗外的光很好，就这样坐了一会儿。");
    add(
        "Vue 3 组合式 API 学习笔记",
        "knowledge",
        "前端开发",
        List.of("Vue", "前端"),
        false,
        3,
        "把相关逻辑放在一起，让组件更容易理解。\n\n"
            + "## 响应式状态\n\n"
            + "```javascript\n"
            + "import { ref, computed } from 'vue'\n\n"
            + "const count = ref(0)\n"
            + "const doubled = computed(() => count.value * 2)\n"
            + "```\n\n"
            + "## ref 与 reactive\n\n"
            + "| 方法 | 适合的场景 |\n"
            + "| --- | --- |\n"
            + "| ref | 单个值或需要整体替换的对象 |\n"
            + "| reactive | 一组相关的响应式属性 |\n\n"
            + "## 下一步\n\n"
            + "试着将组件中的数据请求逻辑提取为 composable。");
    add(
        "读书摘记：专注于重要的事",
        "thoughts",
        "阅读摘记",
        List.of("阅读", "专注"),
        false,
        5,
        "注意力是我们最珍贵、也最容易被忽略的资源。\n\n"
            + "## 三个值得带走的想法\n\n"
            + "1. 每天选择一件真正重要的事。\n"
            + "2. 给复杂的问题留出完整的时间。\n"
            + "3. 用休息保护持续专注的能力。\n\n"
            + "写下计划只是开始，真正的改变发生在一个个具体的选择里。");
    add(
        "一杯咖啡，一段自己的时间",
        "diary",
        "生活碎片",
        List.of("日常", "小确幸"),
        false,
        6,
        "下午的阳光落在桌面上，咖啡还有一点温热。\n\n"
            + "翻开一本读了一半的书，给自己留了四十分钟。没有什么特别的事发生，但就是这样普通的日子，也值得被记住。\n\n"
            + "## 今天的小确幸\n\n"
            + "- 好喝的拿铁\n"
            + "- 恰好喜欢的一首歌\n"
            + "- 提前做完的一件小事");
    add(
        "构建清晰的 REST API",
        "knowledge",
        "后端开发",
        List.of("Java", "API 设计"),
        false,
        8,
        "好的接口会让使用它的人少一些猜测。\n\n"
            + "## 用资源表达意图\n\n"
            + "```java\n"
            + "@GetMapping(\"/api/notes/{id}\")\n"
            + "public Note findNote(@PathVariable String id) {\n"
            + "    return noteService.findOwnedNote(id);\n"
            + "}\n"
            + "```\n\n"
            + "## 安全与一致性\n\n"
            + "- 在服务端检查每一次资源访问\n"
            + "- 明确区分 401、403 与 404\n"
            + "- 使用版本号避免覆盖其他设备的修改\n"
            + "- 为迁移和备份保留清晰的数据格式");
    add(
        "给灵感留一个入口",
        "thoughts",
        "灵感收集",
        List.of("灵感", "写作"),
        false,
        10,
        "想法总是在意想不到的时候出现。\n\n"
            + "不必等到表达完整才开始记录。一个词、一句话、一个还没有答案的问题，都可以成为新的起点。\n\n"
            + "今天的问题：什么样的工具，会让人更愿意表达自己？");
    db.update("INSERT INTO app_meta VALUES('demo-seeded','true')");
  }

  void add(
      String title,
      String space,
      String category,
      List<String> tags,
      boolean favorite,
      int days,
      String content) {
    LocalDate date = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(days);
    content += "\n\n---\n这是一篇非敏感示范记录，可以随时编辑或删除。";
    Note n =
        store.create(
            new Note(
                null, title, content, space, category, tags, favorite, false, false, null, date,
                null, null, 0));
    db.update(
        "UPDATE notes SET draft=FALSE,created_at=?,updated_at=? WHERE id=?",
        date.atTime(9, 30),
        date.atTime(14, 20),
        n.id());
  }
}
