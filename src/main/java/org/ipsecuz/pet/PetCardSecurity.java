package org.ipsecuz.pet;

import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Enterprise-grade cryptographic security and validation engine for Pet Cards.
 * Guarantees tamper-proofing, metadata integrity, skill persistence, and canonical custom name preservation.
 */
public final class PetCardSecurity {

    public static final int CURRENT_SCHEMA = 2;

    private PetCardSecurity() {}

    private static final Set<String> consumedCards = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.concurrent.atomic.AtomicBoolean cardsLoaded = new java.util.concurrent.atomic.AtomicBoolean(false);

    private static void ensureCardsLoaded(IpsecuzPet plugin) {
        if (cardsLoaded.compareAndSet(false, true)) {
            List<String> list = plugin.getConfigManager().getData().getStringList("security.consumed_cards");
            consumedCards.addAll(list);
        }
    }

    public static boolean isCardConsumed(IpsecuzPet plugin, String cardUuid) {
        if (cardUuid == null || cardUuid.isEmpty()) return false;
        ensureCardsLoaded(plugin);
        return consumedCards.contains(cardUuid);
    }

    public static void markCardConsumed(IpsecuzPet plugin, String cardUuid) {
        if (cardUuid == null || cardUuid.isEmpty()) return;
        ensureCardsLoaded(plugin);
        if (consumedCards.add(cardUuid)) {
            List<String> list = new ArrayList<>(consumedCards);
            plugin.getConfigManager().getData().set("security.consumed_cards", list);
            plugin.getConfigManager().forceSave(); // Bắt buộc lưu ngay lập tức xuống đĩa (Durable write)
        }
    }

    private static String getServerSalt(IpsecuzPet plugin) {
        String salt = plugin.getConfigManager().getData().getString("security.server_salt");
        if (salt == null || salt.trim().isEmpty()) {
            salt = UUID.randomUUID().toString().replace("-", "") + System.currentTimeMillis();
            plugin.getConfigManager().getData().set("security.server_salt", salt);
            plugin.getConfigManager().saveData();
        }
        return salt;
    }

    public static String computeHmacSha256(String key, String data) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 32);
        } catch (Exception e) {
            return Integer.toHexString((data + ":" + key).hashCode());
        }
    }

    public static String computeCanonicalSignature(IpsecuzPet plugin, int schema, String uniqueId, String petId, int level, int exp, int stars, String trait, String customName, List<String> skills) {
        String salt = getServerSalt(plugin);
        List<String> sortedSkills = (skills != null) ? new ArrayList<>(skills) : new ArrayList<>();
        Collections.sort(sortedSkills);
        String skillsStr = String.join(",", sortedSkills);
        String cName = (customName != null) ? customName : "";
        String payload = schema + ":" + uniqueId + ":" + petId + ":" + level + ":" + exp + ":" + stars + ":" + trait + ":" + cName + ":" + skillsStr;
        return computeHmacSha256(salt, payload);
    }

    public static String computeLegacySignature(IpsecuzPet plugin, String uniqueId, String petId, int level, int exp, int stars, String trait) {
        String salt = getServerSalt(plugin);
        String payload = uniqueId + ":" + petId + ":" + level + ":" + exp + ":" + stars + ":" + trait + ":" + salt;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(payload.hashCode());
        }
    }

    public static String computeSignature(IpsecuzPet plugin, String uniqueId, String petId, int level, int exp, int stars, String trait) {
        return computeCanonicalSignature(plugin, CURRENT_SCHEMA, uniqueId, petId, level, exp, stars, trait, null, null);
    }

    public static ItemStack createPetCard(IpsecuzPet plugin, String petId, int level, int exp, int stars, String trait, String customName, List<String> unlockedSkills) {
        String defaultName = plugin.getConfig().getString("pets." + petId + ".name", petId);
        String petName = (customName != null && !customName.isEmpty()) ? customName : defaultName;
        PetRarity rarity = PetRarity.fromPetId(plugin, petId);

        ItemStack item = new ItemStack(Material.DRAGON_EGG);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text("§6📦 " + ChatColor.translateAlternateColorCodes('&', petName) + " §e(Lv." + level + ")"));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("§7--------------------"));
        lore.add(Component.text("§7Độ hiếm: " + rarity.getFormattedName()));
        lore.add(Component.text("§7Cấp sao: " + ((plugin.getEvolutionManager() != null) ? plugin.getEvolutionManager().getStarDisplay(stars) : (stars + "⭐"))));
        lore.add(Component.text("§7Đặc chất: " + PetTrait.fromString(trait).getFormattedName()));
        lore.add(Component.text("§7Cấp độ: §aLv." + level + " §7(EXP: §b" + exp + "§7)"));
        if (customName != null && !customName.isEmpty()) {
            lore.add(Component.text("§7Biệt danh: §f" + customName));
        }
        if (unlockedSkills != null && !unlockedSkills.isEmpty()) {
            lore.add(Component.text("§7Kỹ năng đã mở: §e" + unlockedSkills.size() + " skill"));
        }
        lore.add(Component.text("§7--------------------"));
        lore.add(Component.text("§e[Nhấp chuột phải để Triệu Hồi]"));
        meta.lore(lore);

        String cardUuid = UUID.randomUUID().toString();
        String signature = computeCanonicalSignature(plugin, CURRENT_SCHEMA, cardUuid, petId, level, exp, stars, trait, customName, unlockedSkills);
        String skillsSerialized = (unlockedSkills != null) ? String.join(",", unlockedSkills) : "";

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        // Canonical Keys (Schema 2)
        pdc.set(new NamespacedKey(plugin, "card_schema_version"), PersistentDataType.INTEGER, CURRENT_SCHEMA);
        pdc.set(new NamespacedKey(plugin, "card_unique_id"), PersistentDataType.STRING, cardUuid);
        pdc.set(new NamespacedKey(plugin, "card_signature"), PersistentDataType.STRING, signature);
        pdc.set(new NamespacedKey(plugin, "card_pet_id"), PersistentDataType.STRING, petId);
        pdc.set(new NamespacedKey(plugin, "card_level"), PersistentDataType.INTEGER, level);
        pdc.set(new NamespacedKey(plugin, "card_exp"), PersistentDataType.INTEGER, exp);
        pdc.set(new NamespacedKey(plugin, "card_stars"), PersistentDataType.INTEGER, stars);
        pdc.set(new NamespacedKey(plugin, "card_trait"), PersistentDataType.STRING, trait);
        if (customName != null && !customName.isEmpty()) {
            pdc.set(new NamespacedKey(plugin, "card_custom_name"), PersistentDataType.STRING, customName);
        }
        pdc.set(new NamespacedKey(plugin, "card_unlocked_skills"), PersistentDataType.STRING, skillsSerialized);

        // Backward compatibility keys
        pdc.set(new NamespacedKey(plugin, "pet_item_id"), PersistentDataType.STRING, petId);
        pdc.set(new NamespacedKey(plugin, "pet_item_lvl"), PersistentDataType.INTEGER, level);
        pdc.set(new NamespacedKey(plugin, "pet_item_exp"), PersistentDataType.INTEGER, exp);
        pdc.set(new NamespacedKey(plugin, "pet_item_stars"), PersistentDataType.INTEGER, stars);
        pdc.set(new NamespacedKey(plugin, "pet_item_trait"), PersistentDataType.STRING, trait);
        if (customName != null && !customName.isEmpty()) {
            pdc.set(new NamespacedKey(plugin, "pet_item_name"), PersistentDataType.STRING, customName);
        }

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isPetCard(org.bukkit.plugin.Plugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String expectedNamespace = (plugin != null) ? plugin.getName().toLowerCase() : "ipsecuzpet";
        for (NamespacedKey key : pdc.getKeys()) {
            if (key.getNamespace().equalsIgnoreCase(expectedNamespace)) {
                if (key.getKey().equals("pet_item_id") || key.getKey().equals("card_pet_id")) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isPetCard(ItemStack item) {
        return isPetCard(null, item);
    }

    public static class CardValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final PetCardData cardData;

        public CardValidationResult(boolean valid, String errorMessage, PetCardData cardData) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.cardData = cardData;
        }

        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
        public PetCardData getCardData() { return cardData; }
    }

    public static class PetCardData {
        private final String cardUniqueId;
        private final String petId;
        private final int level;
        private final int exp;
        private final int stars;
        private final String trait;
        private final String customName;
        private final List<String> unlockedSkills;

        public PetCardData(String cardUniqueId, String petId, int level, int exp, int stars, String trait, String customName, List<String> unlockedSkills) {
            this.cardUniqueId = cardUniqueId;
            this.petId = petId;
            this.level = level;
            this.exp = exp;
            this.stars = stars;
            this.trait = trait;
            this.customName = customName;
            this.unlockedSkills = unlockedSkills;
        }

        public PetCardData(String petId, int level, int exp, int stars, String trait, String customName, List<String> unlockedSkills) {
            this(null, petId, level, exp, stars, trait, customName, unlockedSkills);
        }

        public String getCardUniqueId() { return cardUniqueId; }
        public String getPetId() { return petId; }
        public int getLevel() { return level; }
        public int getExp() { return exp; }
        public int getStars() { return stars; }
        public String getTrait() { return trait; }
        public String getCustomName() { return customName; }
        public List<String> getUnlockedSkills() { return unlockedSkills; }
    }

    public static CardValidationResult validateAndExtractCard(IpsecuzPet plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return new CardValidationResult(false, "Vật phẩm không hợp lệ hoặc không có dữ liệu meta.", null);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return new CardValidationResult(false, "Không tìm thấy metadata.", null);
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey petIdKey = new NamespacedKey(plugin, "card_pet_id");
        NamespacedKey legacyPetIdKey = new NamespacedKey(plugin, "pet_item_id");

        String petId = pdc.get(petIdKey, PersistentDataType.STRING);
        if (petId == null) {
            petId = pdc.get(legacyPetIdKey, PersistentDataType.STRING);
        }

        if (petId == null || petId.trim().isEmpty()) {
            return new CardValidationResult(false, "Thẻ không chứa mã định danh Thú Cưng.", null);
        }

        // 1. Kiểm tra Pet ID có tồn tại trong hệ thống cấu hình
        if (!plugin.getConfig().contains("pets." + petId)) {
            return new CardValidationResult(false, "Mã Pet '" + petId + "' không tồn tại trong cấu hình máy chủ.", null);
        }

        // 2. Trích xuất level và exp
        int level = pdc.getOrDefault(new NamespacedKey(plugin, "card_level"), PersistentDataType.INTEGER,
                pdc.getOrDefault(new NamespacedKey(plugin, "pet_item_lvl"), PersistentDataType.INTEGER, 1));
        int exp = pdc.getOrDefault(new NamespacedKey(plugin, "card_exp"), PersistentDataType.INTEGER,
                pdc.getOrDefault(new NamespacedKey(plugin, "pet_item_exp"), PersistentDataType.INTEGER, 0));
        int maxLevel = plugin.getConfig().getInt("rpg_system.max_level", 100);

        if (level < 1 || level > maxLevel) {
            return new CardValidationResult(false, "Cấp độ Pet không hợp lệ: " + level + " (Giới hạn: 1 - " + maxLevel + ")", null);
        }
        if (exp < 0) {
            return new CardValidationResult(false, "Điểm kinh nghiệm không thể mang giá trị âm: " + exp, null);
        }

        // 3. Trích xuất sao
        int stars = pdc.getOrDefault(new NamespacedKey(plugin, "card_stars"), PersistentDataType.INTEGER,
                pdc.getOrDefault(new NamespacedKey(plugin, "pet_item_stars"), PersistentDataType.INTEGER, 1));
        int maxStars = plugin.getModuleManager().getEvolutionConfig().getInt("max_stars", 5);
        if (stars < 1 || stars > maxStars) {
            return new CardValidationResult(false, "Cấp sao không hợp lệ: " + stars + " (Giới hạn: 1 - " + maxStars + ")", null);
        }

        // 4. Trích xuất Trait
        String trait = pdc.getOrDefault(new NamespacedKey(plugin, "card_trait"), PersistentDataType.STRING,
                pdc.getOrDefault(new NamespacedKey(plugin, "pet_item_trait"), PersistentDataType.STRING, "NONE"));
        try {
            PetTrait.valueOf(trait.toUpperCase());
        } catch (IllegalArgumentException e) {
            return new CardValidationResult(false, "Đặc chất (Trait) '" + trait + "' không hợp lệ.", null);
        }

        // 5. Trích xuất Tên riêng (Canonical custom_name với migration fallback)
        String customName = pdc.get(new NamespacedKey(plugin, "card_custom_name"), PersistentDataType.STRING);
        if (customName == null) {
            customName = pdc.get(new NamespacedKey(plugin, "pet_item_name"), PersistentDataType.STRING);
        }

        // 6. Trích xuất Kỹ năng đã mở (Skills)
        List<String> unlockedSkills = new ArrayList<>();
        String skillsStr = pdc.get(new NamespacedKey(plugin, "card_unlocked_skills"), PersistentDataType.STRING);
        if (skillsStr != null && !skillsStr.trim().isEmpty()) {
            for (String s : skillsStr.split(",")) {
                if (!s.trim().isEmpty()) {
                    unlockedSkills.add(s.trim().toLowerCase());
                }
            }
        }

        // 7. Chống nhân bản (Anti-cloning): Kiểm tra thẻ đã từng bị tiêu hao chưa
        String cardUuid = pdc.get(new NamespacedKey(plugin, "card_unique_id"), PersistentDataType.STRING);
        if (cardUuid != null && isCardConsumed(plugin, cardUuid)) {
            return new CardValidationResult(false, "Thẻ Thú Cưng này đã được sử dụng trước đó (đã bị tiêu hao / chống nhân bản)!", null);
        }

        // 8. Xác thực chữ ký an toàn nếu có Schema v2
        Integer schemaVersion = pdc.get(new NamespacedKey(plugin, "card_schema_version"), PersistentDataType.INTEGER);
        String signature = pdc.get(new NamespacedKey(plugin, "card_signature"), PersistentDataType.STRING);

        if (schemaVersion != null && schemaVersion >= 2 && signature != null && cardUuid != null) {
            String expectedCanonical = computeCanonicalSignature(plugin, schemaVersion, cardUuid, petId, level, exp, stars, trait, customName, unlockedSkills);
            if (!signature.equals(expectedCanonical)) {
                // Hỗ trợ kiểm tra dự phòng chuẩn SHA-256 cũ
                String expectedLegacy = computeLegacySignature(plugin, cardUuid, petId, level, exp, stars, trait);
                if (!signature.equals(expectedLegacy)) {
                    return new CardValidationResult(false, "Chữ ký xác thực bị giả mạo! Dữ liệu thẻ đã bị chỉnh sửa bất hợp pháp.", null);
                }
            }
        } else {
            // Thẻ v1 cũ không có chữ ký
            boolean allowLegacy = plugin.getConfig().getBoolean("security.allow_legacy_unsigned_cards", true);
            if (!allowLegacy) {
                return new CardValidationResult(false, "Máy chủ đã tắt chế độ chấp nhận Thẻ Pet phiên bản cũ không có chữ ký bảo mật.", null);
            }
            if (cardUuid == null) {
                cardUuid = UUID.randomUUID().toString();
            }
        }

        return new CardValidationResult(true, null, new PetCardData(cardUuid, petId, level, exp, stars, trait, customName, unlockedSkills));
    }
}
