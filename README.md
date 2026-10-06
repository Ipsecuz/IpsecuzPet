# IpsecuzPet

IpsecuzPet is a Minecraft Paper/Folia pet RPG plugin with collectible pets, pet stats, leveling, capture balls, shop purchases, duels, mounts, custom models, and configurable messages.

## Features

- **Pet ownership system**: players can own, summon, despawn, rename, withdraw, and redeem pets.
- **Pet GUI**: `/pet` opens the player pet profile menu, where pets can be summoned or stored with one click.
- **Pet shop GUI**: `/pet shop` opens a shop menu for buying pets from `config.yml`.
- **Multiple currency types**:
  - `ITEM` purchases using Minecraft materials.
  - `MONEY` purchases through Vault economy.
  - `POINTS` purchases through PlayerPoints.
- **RPG stat system**: pets have damage, health, defense, speed, and intelligence stats.
- **Leveling system**: active pets gain EXP when the owner kills monsters.
- **Configurable stat growth**: pet stats scale by level using values from `rpg_system.default_growth`.
- **Permission-based EXP multipliers**: grant permissions such as `ipsecuzpet.multiplier.1.5` or `ipsecuzpet.multiplier.2.0` for boosted pet EXP.
- **Pet buffs**: pets can apply potion effects to their owner, configured per pet.
- **Pet particles**: each pet can display a configurable particle effect.
- **Pet following AI**: pets follow their owner and teleport back when too far away.
- **Combat assist**: pets can target enemies attacked by the owner and respond when the owner is attacked.
- **Pet duel system**: players can challenge each other to pet duels with `/pet duel <player>` and `/pet accept`.
- **Pet death and revival**: dead pets are marked as dead and can be revived by mining diamond ore or deepslate diamond ore.
- **Capture system**: players can catch configured mobs using custom capture balls.
- **Capture ball rules**: each ball supports whitelist or blacklist capture modes, custom chances, materials, names, and lore.
- **Permission-based capture bonuses**: permissions like `ipsecuzpet.catch.vip` and `ipsecuzpet.catch.mvp` increase capture chance.
- **Pet item withdrawal**: `/pet withdraw <pet_id>` converts an inactive pet into a redeemable item that stores its level and EXP.
- **Mount support**: owners can right-click their pet to ride it.
- **Quick stats view**: shift-right-click an owned pet or use `/pet stats` to view pet stats.
- **BetterModel support**: pets can use BetterModel models via `model_id`, with idle/walk animation updates.
- **Folia support**: scheduling helpers support both Folia and Paper/Spigot-style servers.
- **Customizable language file**: messages are stored in `messages.yml` and support legacy color codes plus hex colors in `&#RRGGBB` format.
- **Admin tools**: admins can give pets, give capture balls, and reload plugin configuration.
- **Update checker**: admins are notified when a newer Spigot resource version is available.

## Requirements

- Java 21
- Paper/Folia-compatible Minecraft server
- Plugin API version: `1.19`
- Built against Paper API `1.20.4` and Folia API `1.20.1`

### Optional dependencies

These are only needed for the related features:

| Dependency | Used for |
| --- | --- |
| BetterModel | Custom pet models through `model_id` |
| Vault + an economy plugin | `MONEY` pet purchases |
| PlayerPoints | `POINTS` pet purchases |

## Installation

1. Build or download the plugin JAR.
2. Place the JAR in your server's `plugins` folder.
3. Install optional dependencies if you use BetterModel, Vault economy, or PlayerPoints features.
4. Restart the server.
5. Edit `plugins/IpsecuzPet/config.yml` and `plugins/IpsecuzPet/messages.yml` as needed.
6. Use `/pet reload` after changing configuration files.

## Building from source

This project uses Maven.

```bash
mvn clean package
```

The compiled plugin JAR will be generated in the `target/` folder.

> Note: the project references `libs/BetterModel.jar` with Maven `system` scope, so keep that file in place when building unless you change the dependency setup.

## Commands

Main command: `/ipsecuzpet`  
Alias: `/pet`

| Command | Description | Permission |
| --- | --- | --- |
| `/pet` | Open the pet profile GUI | `ipsecuzpet.use` |
| `/pet help` | Show the command list | `ipsecuzpet.use` |
| `/pet shop` | Open the pet shop GUI | `ipsecuzpet.use` |
| `/pet despawn` | Store the active pet | `ipsecuzpet.use` |
| `/pet stats` | Show active pet stats | `ipsecuzpet.use` |
| `/pet info` | Alias for `/pet stats` | `ipsecuzpet.use` |
| `/pet rename <name>` | Rename the active pet | `ipsecuzpet.rename` |
| `/pet withdraw <pet_id>` | Convert an inactive pet into a redeemable item | `ipsecuzpet.use` |
| `/pet duel <player>` | Send a pet duel request | `ipsecuzpet.use` |
| `/pet accept` | Accept a pet duel request | `ipsecuzpet.use` |
| `/pet give <player> <pet_id>` | Give a pet to a player | `ipsecuzpet.admin` |
| `/pet giveball <player> <ball_id> [amount]` | Give capture balls to a player | `ipsecuzpet.admin` |
| `/pet reload` | Reload config, messages, data, and capture balls | `ipsecuzpet.admin` |

## Permissions

| Permission | Description | Default / note |
| --- | --- | --- |
| `ipsecuzpet.use` | Allows basic pet commands, GUI, shop, duel, and help | `true` |
| `ipsecuzpet.admin` | Allows admin commands | `op` |
| `ipsecuzpet.rename` | Allows `/pet rename` | Assign with a permission plugin |
| `ipsecuzpet.maxslots.<amount>` | Raises a player's pet slot limit, for example `ipsecuzpet.maxslots.5` | Assign with a permission plugin |
| `ipsecuzpet.multiplier.1.5` | Gives 1.5x pet EXP | Configurable in `config.yml` |
| `ipsecuzpet.multiplier.2.0` | Gives 2.0x pet EXP | Configurable in `config.yml` |
| `ipsecuzpet.catch.vip` | Adds capture chance bonus from `capture_system.permission_bonus.vip` | Configurable in `config.yml` |
| `ipsecuzpet.catch.mvp` | Adds capture chance bonus from `capture_system.permission_bonus.mvp` | Configurable in `config.yml` |

## Default pet content

The default configuration includes **43 pets** across three purchase types:

| Currency type | Count | Examples |
| --- | ---: | --- |
| `ITEM` | 15 | Chicken, Pig, Cow, Skeleton, Creeper, Dolphin, Shulker, Parrot |
| `MONEY` | 16 | Wolf, Cat, Fox, Iron Golem, Blaze, Enderman, Turtle, Pillager |
| `POINTS` | 12 | Wither, Ender Dragon, Warden, Ravager, Allay, Frog, Phantom, Vex |

Each pet can define:

- Vanilla entity type
- Display name
- GUI icon
- Price and currency type
- Catchable status
- Particle effect
- Owner potion effects
- RPG stats
- Optional BetterModel `model_id`

## Capture balls

The default config includes three capture balls:

| Ball ID | Material | Base chance | Mode | Notes |
| --- | --- | ---: | --- | --- |
| `basic_ball` | Egg | 20% | Whitelist | Only catches selected passive mobs |
| `ultra_ball` | Snowball | 50% | Blacklist | Cannot catch boss-type mobs by default |
| `master_ball` | Slime Ball | 100% | Blacklist | Can catch all configured types unless blacklisted |

Capture balls are configured in `capture_system.items`.

## Configuration overview

Most gameplay settings are in `src/main/resources/config.yml` before building, or `plugins/IpsecuzPet/config.yml` after the plugin runs.

Important sections:

```yaml
max_pets: 2

rpg_system:
  max_level: 100
  base_exp_requirement: 50
  exp_per_kill: 10
  revive_cost_diamonds: 32
  default_growth:
    damage: 0.5
    health: 2.0
    defense: 0.2
    speed: 0.001
    intelligence: 0.5

pets:
  chicken_pet:
    type: CHICKEN
    name: "&eGà Con Lon Ton"
    icon: FEATHER
    price: 32
    currency: ITEM
    material: WHEAT_SEEDS
    catchable: true
    particle: "VILLAGER_HAPPY"
    effects: ["SLOW_FALLING:0"]
    stats:
      damage: 2.0
      health: 10.0
      defense: 0.0
      speed: 0.3
      intelligence: 5.0
```

## Adding a custom pet

Add a new entry under `pets:` in `config.yml`.

```yaml
pets:
  red_dragon_pet:
    type: ENDER_DRAGON
    name: "&cRed Dragon"
    model_id: "red_dragon"
    icon: DRAGON_HEAD
    price: 200
    currency: POINTS
    catchable: false
    particle: "FLAME"
    effects: ["INCREASE_DAMAGE:1", "FIRE_RESISTANCE:0"]
    stats:
      damage: 15.0
      health: 100.0
      defense: 5.0
      speed: 0.35
      intelligence: 10.0
```

If you use BetterModel, make sure `model_id` matches a valid BetterModel model key. The plugin attempts to play `idle` and `walk` animations when available.

## Data and messages

- `data.yml` stores player pet data, levels, EXP, status, custom names, and revive progress.
- `messages.yml` stores all user-facing messages.
- Messages support placeholders such as `%pet_name%`, `%player%`, `%level%`, `%exp%`, `%req%`, `%current%`, and `%max%` depending on the message.
- Color formats supported by the language manager:
  - Legacy colors: `&a`, `&e`, `&l`, etc.
  - Hex colors: `&#00AAFF`

## Notes for server owners

- Use a permission plugin to assign non-default permission nodes such as `ipsecuzpet.rename`, `ipsecuzpet.maxslots.<amount>`, EXP multipliers, and capture bonuses.
- Install Vault and an economy plugin before using `MONEY` pets.
- Install PlayerPoints before using `POINTS` pets.
- Configure BetterModel models before using custom `model_id` values.
- Always back up `data.yml` before making major changes to pet IDs or player data.

---

## 🛠️ Phân tích lỗi & Cách khắc phục (Bugfixes & Error Guide)

### 1. Lỗi BetterModel: `Không tìm thấy Model ID: ender_dragon_pet / allay_pet`

#### Triệu chứng lỗi (Server Console Log):
```log
[WARN]: [IpsecuzPet] Không tìm thấy Model ID: ender_dragon_pet. Hãy kiểm tra lại cấu hình của BetterModel.
[WARN]: [IpsecuzPet] Các ID model hiện có: {hv_vespera, hv_vespera_projectile, skeleton_boss_poison_vfx, ...}
[WARN]: [IpsecuzPet] Không tìm thấy Model ID: allay_pet. Hãy kiểm tra lại cấu hình của BetterModel.
```

#### Nguyên nhân kỹ thuật:
1. **Fallback sai giá trị mặc định trong `PetManager.java`:**
   ```java
   // Code cũ:
   String modelId = plugin.getConfig().getString("pets." + petId + ".model_id", petId);
   modelHandler.spawnModel(player, pet, modelId);
   ```
   Khi một pet trong `config.yml` không khai báo `model_id` (chẳng hạn như `ender_dragon_pet` hoặc `allay_pet`), plugin tự động lấy chính `petId` làm `modelId` mặc định. BetterModel nhận yêu cầu tìm kiếm model `ender_dragon_pet` và `allay_pet`. Do trong thư mục model của server không có các model này nên BetterModel đưa ra cảnh báo WARN và liệt kê danh sách các model hiện có.
2. **Mob gốc bị tàng hình vô điều kiện:**
   ```java
   // Code cũ trong PetManager.java:
   if (pet instanceof LivingEntity living) {
       living.setInvisible(true); // Ẩn mob gốc cho mọi loại pet!
       ...
   }
   ```
   Dòng lệnh ẩn mob gốc chạy vô điều kiện cho cả mob vanilla. Khi model BetterModel không tải được, mob gốc lại bị tàng hình, dẫn đến việc **pet biến mất hoàn toàn và người chơi không thấy gì**.

#### Giải pháp khắc phục trong mã nguồn:
Trong `PetManager.java` (hàm `spawnPet`):
```java
// Chỉ lấy modelId nếu có cấu hình trong config, mặc định là null
String modelId = plugin.getConfig().getString("pets." + petId + ".model_id", null);

if (pet instanceof LivingEntity living) {
    living.setRemoveWhenFarAway(false);
    living.setCanPickupItems(false);
    living.setCollidable(false);

    // CHỈ ẩn mob gốc khi có model tùy chỉnh hợp lệ
    if (modelId != null && !modelId.trim().isEmpty()) {
        living.setInvisible(true);
        modelHandler.spawnModel(player, pet, modelId);
    } else {
        living.setInvisible(false); // Mob vanilla luôn luôn hiển thị
    }

    if (plugin.getConfig().getBoolean("pets." + petId + ".silent", true)) {
        living.setSilent(true);
    }
}
```

---

## 💡 Cập nhật theo Feedback người chơi (Player Feedback Updates)

### 1. Tùy chọn Kích thước Pet Vanilla: Bé con (Baby) & Trưởng thành (Adult)

> *"maybe let players choose whether they want their vanilla pets to stay baby-sized or adult-sized. i think having that option would make the pet system feel a lot more customizable and fun"*

#### Thiết kế & Triển khai:
- **Lưu trữ dữ liệu trong `data.yml`:**
  Thêm trường boolean `<uuid>.pets.<petId>.is_baby` (mặc định: `false`).
- **Xử lý khi triệu hồi mob trong `PetManager.java`:**
  Khóa độ tuổi bằng `setAgeLock(true)` để thú cưng con không tự lớn thành thú trưởng thành theo thời gian:
  ```java
  boolean isBaby = plugin.getConfigManager().isPetBaby(player.getUniqueId(), petId);
  if (pet instanceof Ageable ageable) {
      if (isBaby) {
          ageable.setBaby();
          ageable.setAgeLock(true); // Ngăn mob tự lớn lên
      } else {
          ageable.setAdult();
      }
  } else if (pet instanceof Zombie zombie) {
      zombie.setBaby(isBaby);
  } else if (pet instanceof Piglin piglin) {
      piglin.setBaby(isBaby);
  }
  ```
- **Cách người chơi thao tác:**
  - Lệnh: `/pet baby` hoặc `/pet form` để bật/tắt nhanh kích thước cho pet đang kích hoạt.
  - GUI Hồ Sơ Pet (`/pet`): Nhấp chuột phải (Right-Click) vào pet để chuyển đổi giữa `[👶 Bé con]` và `[🦁 Trưởng thành]`.

---

### 2. Hệ thống Ấp Trứng Pet (Pet Egg Hatching System)

> *"add a pet hatching system, where players can find eggs and hatch them into pets. 🥹"*

#### Cơ chế hoạt động:
1. **Phân cấp độ hiếm của Trứng:**
   - 🥚 **Common Egg (Trứng Thường):** Nở ra các pet cơ bản như Gà, Heo, Bò, Cừu.
   - 🔮 **Rare Egg (Trứng Hiếm):** Nở ra Sói, Mèo, Cáo, Golem Sắt.
   - ⚡ **Epic Egg (Trứng Sử Thi):** Nở ra Blaze, Enderman, Quái thú.
   - 👑 **Legendary / Mythic Egg (Trứng Thần Thoại):** Nở ra Rồng Ender, Warden, Wither, Allay.

2. **Cấu hình mẫu trong `config.yml`:**
   ```yaml
   hatching_system:
     enabled: true
     eggs:
       common_egg:
         name: "&a🥚 Trứng Pet Thường"
         material: EGG
         lore:
           - "&7Độ hiếm: &aPhổ Biến"
           - "&7Nhấp chuột phải để ấp nở một thú cưng ngẫu nhiên!"
           - "&e[Nhấp chuột phải để ấp]"
         loot_table:
           chicken_pet: 35
           pig_pet: 30
           cow_pet: 25
           sheep_pet: 10
       legendary_egg:
         name: "&6👑 Trứng Pet Thần Thoại"
         material: DRAGON_EGG
         lore:
           - "&7Độ hiếm: &6Thần Thoại"
           - "&7Chứa đựng linh hồn của sinh vật tối thượng!"
           - "&e[Nhấp chuột phải để ấp]"
         loot_table:
           allay_pet: 40
           warden_pet: 30
           ender_dragon_pet: 20
           wither_pet: 10
   ```

3. **Cơ chế ấp trứng & Hiệu ứng tương tác:**
   - Người chơi cầm trứng và nhấp chuột phải (`PlayerInteractEvent`).
   - Kiểm tra giới hạn slot pet hiện có (`max_pets`). Nếu đầy -> Hủy thao tác và gửi thông báo.
   - Trừ 1 quả trứng trong tay.
   - Phát âm thanh nứt vỏ trứng (`Sound.BLOCK_TURTLE_EGG_CRACK`) và hiệu ứng hạt (`Particle.FIREWORK`, `Particle.TOTEM_OF_UNDYING`).
   - Chọn pet ngẫu nhiên dựa trên tỉ lệ trọng số (Weight-based Random).
   - Nếu đã sở hữu: Thưởng lượng EXP tương ứng hoặc bồi thường mảnh nguyên liệu.
   - Màn hình hiển thị Title chúc mừng kèm hiệu ứng âm thanh ăn mừng.
4. **Lệnh Admin:**
   - `/pet giveegg <player> <egg_id> [amount]` - Cấp phát trứng cho người chơi hoặc tích hợp vào hệ thống Quest / Shop / Drop từ Quái vật.

---

## 🚀 Kế hoạch & Đề xuất Update tiếp theo (Feature Roadmap)

| STT | Tính năng đề xuất | Chi tiết tính năng | Giá trị mang lại |
|---|---|---|---|
| **1** | **Kỹ năng Pet (Pet Skills & Ultimate)** | Bổ sung **Nội tại (Passive)** và **Tuyệt chiêu kích hoạt (Active Skill)** cho từng loài pet (Ví dụ: Ender Dragon phun lửa diện rộng, Warden bắn sóng âm đẩy lùi quái, Sói tăng sát thương chí mạng). | Biến pet thành trợ thủ đắc lực trong chiến đấu RPG thay vì chỉ đi theo sau làm cảnh. |
| **2** | **Thức ăn & Độ vui vẻ (Pet Food & Happiness)** | Mỗi loài pet có chế độ ăn riêng. Khi được cho ăn đều đặn và độ vui vẻ đạt 100%, pet được tăng tốc chạy, x1.2 EXP và hiệu quả buff potion cao hơn. | Tăng tính tương tác chăm sóc, tạo đầu ra giá trị cho nông sản và thức ăn trong game. |
| **3** | **Cải tiến AI Bay (Flying / Shoulder AI)** | Khắc phục hạn chế của các mob bay (Allay, Bat, Bee, Ender Dragon) khi không dùng được `moveTo()` trên mặt đất. Bổ sung AI bay lượn vòng cung hoặc cơ chế đậu lên vai chủ nhân. | Trải nghiệm di chuyển mượt mà, không bị kẹt địa hình hay đứng đơ khi chủ nhân chạy nhanh. |
| **4** | **GUI Đa Trang & Bảng điều khiển Pet** | Thay thế rương 54 ô đơn lẻ bằng giao diện phân trang `[◄ Trang trước / Trang sau ►]` và menu riêng cho từng pet (Triệu hồi, Đổi dạng Bé/Lớn, Đổi tên, Rút thẻ, Nâng cấp). | Hỗ trợ không giới hạn số lượng pet, giao diện trực quan và chuyên nghiệp. |
| **5** | **Tiến hóa & Tăng sao (Evolution & Star Fusion)** | Cho phép dung hợp 2 pet cùng loại hoặc dùng nguyên liệu khi đạt cấp tối đa để tăng lên ⭐ 2 Sao, 3 Sao, gia tăng chỉ số và mở khóa hiệu ứng hạt độc quyền. | Giúp người chơi cày cuốc lâu dài, giải quyết vấn đề khi mở trùng pet từ trứng. |
| **6** | **Giao dịch Pet an toàn (`/pet trade <player>`)** | Mở menu GUI trao đổi pet an toàn 2 bên giữa người chơi với nhau. | Phát triển nền kinh tế và trao đổi thú cưng sôi động trong cộng đồng server. |

---

## 🌟 Bản Cập Nhật Lớn: IpsecuzPet V2.0 (Completed & Verified)

Toàn bộ các tính năng đề xuất và sửa lỗi đã được **triển khai hoàn tất và kiểm thử biên dịch thành công (BUILD SUCCESS)**:

### 1. 🛡️ Khắc phục toàn bộ lỗi BetterModel & Mob Vanilla tàng hình
- **Sửa triệt để Fallback sai:** Trong `PetManager.java`, `model_id` chỉ được gọi khi có khai báo rõ ràng trong `config.yml`. Khi không có model (như `ender_dragon_pet`, `allay_pet`), plugin không còn spam cảnh báo `[WARN] Không tìm thấy Model ID` nữa.
- **Sửa lỗi tàng hình:** Mob vanilla luôn luôn hiển thị (`living.setInvisible(false)`). Chỉ khi nào BetterModel gắn model 3D thành công thì mob gốc mới được ẩn đi.

### 2. 👶 Tùy chọn Kích thước Pet Vanilla: Bé con (Baby) & Trưởng thành (Adult)
- **Hỗ trợ toàn diện:** Áp dụng cho toàn bộ động vật `Ageable`, cũng như `Zombie` (Baby Zombie), `Piglin` (Baby Piglin)...
- **Khóa tuổi vĩnh viễn (`setAgeLock(true)`):** Đảm bảo thú cưng con không bị tự lớn lên theo thời gian.
- **Cách sử dụng:**
  - Lệnh nhanh: `/pet baby` hoặc `/pet form` để đổi kích thước ngay lập tức cho pet đang kích hoạt.
  - GUI: Nhấp chuột phải vào Pet trong menu `/pet` -> mở **Bảng Điều Khiển Chi Tiết** -> Nhấp nút **[KÍCH THƯỚC: Bé con 👶 / Trưởng thành 🦁]**.

### 3. 🥚 Hệ thống Ấp Trứng Pet (Hatching System) với GUI & Hỗ trợ NPC
- **Giao diện Lò Ấp Trứng:** Dùng lệnh `/pet hatch` (alias: `/pet incubator`).
- **Tích hợp NPC mượt mà (FancyNpcs, Citizens, ZNPCs...):**
  - **Citizens:** `/npc cmd add pet hatch`
  - **FancyNpcs:** Chọn NPC -> Actions -> Add Command -> `pet hatch` (hoặc Player Command)
  - **ZNPCs:** `/znpc action <id> CMD pet hatch`
- **Tương tác trực tiếp:** Người chơi có thể cầm trứng nhấp chuột phải ngoài thế giới hoặc nhấp trực tiếp trong GUI lò ấp.
- **Cấu hình tùy biến:** Nằm riêng tại `modules/hatching.yml` với bảng tỉ lệ rơi (Loot Table), giá tiền/points, và tỉ lệ rơi trứng khi đào quặng hoặc đánh boss.
- **Lệnh Admin:** `/pet giveegg <player> <egg_id> [amount]` (Tab completion hỗ trợ tự động gợi ý ID trứng).

### 4. 📁 Cấu trúc Thư Mục Mô-đun Riêng Biệt (`modules/*.yml`)
Toàn bộ các tính năng lớn đã được tách biệt thành từng file cấu hình độc lập để admin dễ quản lý:
- `modules/hatching.yml`: Cấu hình hệ thống ấp trứng, các loại trứng, tỉ lệ nở.
- `modules/skills.yml`: Cấu hình kỹ năng Nội tại (Passive) và Tuyệt chiêu kích hoạt (Ultimate AoE) cho từng loài pet.
- `modules/feeding.yml`: Cấu hình thức ăn, độ no/vui vẻ, buff EXP và tốc độ chạy.
- `modules/evolution.yml`: Cấu hình tăng cấp sao (1⭐ -> 5⭐), điều kiện level và nguyên liệu.
- `modules/trade.yml`: Cấu hình giao dịch thú cưng an toàn giữa 2 người chơi.

### 5. 🌐 Hỗ trợ Đa Phiên Bản Động (1.20 -> 1.21.x -> 26.x Auto-Detection)
- Tích hợp lớp `DynamicPetRegistry`: Tự động quét `EntityType` của server lúc khởi động.
- Khi server chạy trên 1.21+ hoặc các phiên bản tương lai (1.22 ... 26.x), plugin tự động phát hiện các loài mob mới (như `Breeze`, `Bogged`, `Armadillo`...) và tự động đăng ký vào hệ thống Pet với chỉ số cân bằng, không cần phải nhập tay vào config.
- Hoàn toàn an toàn, không gây lỗi `ClassNotFoundException` hay `NoSuchFieldError` trên các phiên bản thấp hơn.

### 6. ⚡ Tối ưu An Toàn Tuyệt Đối Cho Folia & Multi-Threading
- Mọi thao tác thay đổi Entity (teleport, đổi baby, gán potion, spawn, xóa model) đều chạy trong `SchedulerUtils.runEntityTask(plugin, entity, ...)` trên Thread Region riêng của entity đó.
- **AI Bay mượt mà (Flying AI):** Các mob bay như Allay, Bat, Bee, Phantom, Ender Dragon... được áp dụng thuật toán bay lượn vector bám theo vai/đầu người chơi, không dùng `moveTo()` trên mặt đất nên không bị kẹt địa hình hay crash Folia.
- Tránh hoàn toàn việc truy cập Entity chéo Region gây gián đoạn Main Thread.

### 7. 🎮 Danh Sách Lệnh Mới Đầy Đủ
| Lệnh | Mô tả | Quyền hạn |
|---|---|---|
| `/pet` | Mở GUI Hồ Sơ Pet (Hỗ trợ phân trang nhiều trang) | `ipsecuzpet.use` |
| `/pet hatch` | Mở GUI Lò Ấp Trứng (Dùng gắn cho NPC) | `ipsecuzpet.use` |
| `/pet baby` | Chuyển đổi kích thước thú cưng (Bé con 👶 / Trưởng thành 🦁) | `ipsecuzpet.use` |
| `/pet feed` | Cầm thức ăn trên tay cho thú cưng ăn | `ipsecuzpet.use` |
| `/pet skill` | Kích hoạt Tuyệt chiêu Nộ của Pet | `ipsecuzpet.use` |
| `/pet star` | Nâng cấp cấp sao (1⭐ - 5⭐) tăng 15% chỉ số mỗi sao | `ipsecuzpet.use` |
| `/pet trade <player>` | Gửi lời mời giao dịch Pet an toàn | `ipsecuzpet.use` |
| `/pet trade accept` | Chấp nhận giao dịch Pet | `ipsecuzpet.use` |
| `/pet giveegg <player> <id> [số lượng]` | Cấp phát trứng Pet cho người chơi | `ipsecuzpet.admin` |
| `/pet reload` | Nạp lại config chính, ngôn ngữ và toàn bộ `modules/*.yml` | `ipsecuzpet.admin` |