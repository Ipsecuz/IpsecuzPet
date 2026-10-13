package org.ipsecuz.pet.dialog;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.ipsecuz.pet.IpsecuzPet;
import org.ipsecuz.pet.SchedulerUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Isolated PaperMC Native Dialog Engine implementing Paper 1.21.7+ Dialog API via safe runtime reflection.
 * Ensures strict zero class-loading errors on older versions while providing 100% genuine Dialog objects,
 * dynamic inputs, action buttons, and callback dispatch on compatible runtimes.
 */
public class PaperNativeDialogEngine {

    public enum Stage {
        CREATE_CALLBACK_OPTIONS("1. Create click callback options"),
        CREATE_BUTTON_ACTION("2. Create button action"),
        CONSTRUCT_ACTION_BUTTON("3. Construct ActionButton"),
        CONSTRUCT_MULTI_ACTION_TYPE("4. Construct MultiActionType"),
        BUILD_DIALOG_BASE("5. Build DialogBase"),
        BUILD_REGISTRY_ENTRY("6. Build DialogRegistryEntry"),
        CREATE_NATIVE_DIALOG("7. Create native Dialog"),
        INVOKE_SHOW_DIALOG("8. Invoke Audience.showDialog");

        private final String description;
        Stage(String description) { this.description = description; }
        public String getDescription() { return description; }
    }

    private final IpsecuzPet plugin;
    private final boolean available;
    private final String initStatusMessage;
    private final boolean classAvailable;
    private final boolean methodsAvailable;

    private volatile boolean actionButtonCreationTested;
    private volatile boolean multiActionConstructionTested;
    private volatile boolean emptyActionGuardTested;
    private volatile boolean callbackOptionsInitialized;

    // Operation tracking
    private volatile String lastAttemptType;
    private volatile long lastAttemptTimestamp;
    private volatile boolean lastAttemptSuccess;
    private volatile String lastAttemptPlayer;

    // Active failure state
    private volatile boolean hasActiveFailure;
    private volatile Stage lastFailureStage;
    private volatile String lastFailureReason;
    private volatile String lastRootExceptionType;
    private volatile String lastRootExceptionMessage;
    private volatile long lastFailureTimestamp;

    // Historical failure tracking
    private volatile Stage historicalFailureStage;
    private volatile String historicalFailureReason;
    private volatile String historicalRootExceptionType;
    private volatile String historicalRootExceptionMessage;
    private volatile long historicalFailureTimestamp;

    // Reflection cache
    private Class<?> dialogClass;
    private Class<?> dialogBaseClass;
    private Class<?> dialogBaseBuilderClass;
    private Class<?> dialogBodyClass;
    private Class<?> dialogTypeClass;
    private Class<?> actionButtonClass;
    private Class<?> dialogActionClass;
    private Class<?> dialogActionCallbackClass;
    private Class<?> dialogInputClass;
    private Class<?> dialogResponseViewClass;
    private Class<?> registryBuilderFactoryClass;
    private Class<?> dialogRegistryEntryBuilderClass;
    private Class<?> clickCallbackOptionsClass;

    private Method dialogCreateMethod;
    private Method dialogBaseBuilderMethod;
    private Method dialogBodyPlainMessageMethod;
    private Method actionButtonCreateMethod;
    private Method dialogActionCustomClickMethod;
    private Method dialogTypeNoticeMethod;
    private Method dialogTypeConfirmationMethod;
    private Method dialogTypeMultiActionMethod;
    private Method dialogTypeMultiActionWithExitMethod;
    private Method dialogTypeMultiActionWithExit2Method;
    private Method dialogInputTextMethod;
    private Method dialogInputBoolMethod;
    private Method dialogInputRangeMethod;
    private Method showDialogMethod;
    private Object defaultClickOptions;
    private final ConcurrentHashMap<String, Long> logRateLimitMap = new ConcurrentHashMap<>();

    public interface NativeDialogResponse {
        String getText(String key);
        Boolean getBoolean(String key);
        Float getFloat(String key);
    }

    @FunctionalInterface
    public interface NativeDialogActionHandler {
        void handle(Player player, NativeDialogResponse response);
    }

    public record NativeButton(Component label, Component tooltip, int width, NativeDialogActionHandler handler) {}

    public PaperNativeDialogEngine(IpsecuzPet plugin) {
        this.plugin = plugin;
        boolean clsAvail = false;
        boolean methAvail = false;
        String statusMsg = "";
        boolean initialized = false;

        try {
            dialogClass = Class.forName("io.papermc.paper.dialog.Dialog");
            dialogBaseClass = Class.forName("io.papermc.paper.registry.data.dialog.DialogBase");
            dialogBaseBuilderClass = Class.forName("io.papermc.paper.registry.data.dialog.DialogBase$Builder");
            dialogBodyClass = Class.forName("io.papermc.paper.registry.data.dialog.body.DialogBody");
            dialogTypeClass = Class.forName("io.papermc.paper.registry.data.dialog.type.DialogType");
            actionButtonClass = Class.forName("io.papermc.paper.registry.data.dialog.ActionButton");
            dialogActionClass = Class.forName("io.papermc.paper.registry.data.dialog.action.DialogAction");
            dialogActionCallbackClass = Class.forName("io.papermc.paper.registry.data.dialog.action.DialogActionCallback");
            dialogInputClass = Class.forName("io.papermc.paper.registry.data.dialog.input.DialogInput");
            dialogResponseViewClass = Class.forName("io.papermc.paper.dialog.DialogResponseView");
            registryBuilderFactoryClass = Class.forName("io.papermc.paper.registry.RegistryBuilderFactory");
            dialogRegistryEntryBuilderClass = Class.forName("io.papermc.paper.registry.data.dialog.DialogRegistryEntry$Builder");
            clickCallbackOptionsClass = Class.forName("net.kyori.adventure.text.event.ClickCallback$Options");
            clsAvail = true;

            dialogCreateMethod = dialogClass.getMethod("create", Consumer.class);
            try {
                dialogBaseBuilderMethod = dialogBaseClass.getMethod("builder", Component.class);
            } catch (NoSuchMethodException e) {
                dialogBaseBuilderMethod = dialogBaseClass.getMethod("builder");
            }
            dialogBodyPlainMessageMethod = dialogBodyClass.getMethod("plainMessage", Component.class);

            try {
                actionButtonCreateMethod = actionButtonClass.getMethod("create", Component.class, Component.class, int.class, dialogActionClass);
            } catch (NoSuchMethodException ignored) {}

            dialogActionCustomClickMethod = dialogActionClass.getMethod("customClick", dialogActionCallbackClass, clickCallbackOptionsClass);

            try {
                dialogTypeNoticeMethod = dialogTypeClass.getMethod("notice", actionButtonClass);
            } catch (NoSuchMethodException ignored) {}

            try {
                dialogTypeConfirmationMethod = dialogTypeClass.getMethod("confirmation", actionButtonClass, actionButtonClass);
            } catch (NoSuchMethodException ignored) {}

            try {
                dialogTypeMultiActionWithExitMethod = dialogTypeClass.getMethod("multiAction", List.class, actionButtonClass, int.class);
            } catch (NoSuchMethodException ignored) {}

            try {
                dialogTypeMultiActionWithExit2Method = dialogTypeClass.getMethod("multiAction", List.class, actionButtonClass);
            } catch (NoSuchMethodException ignored) {}

            try {
                dialogTypeMultiActionMethod = dialogTypeClass.getMethod("multiAction", List.class);
            } catch (NoSuchMethodException ignored) {}

            try {
                dialogInputTextMethod = dialogInputClass.getMethod("text", String.class, Component.class);
            } catch (NoSuchMethodException ignored) {}

            try {
                dialogInputBoolMethod = dialogInputClass.getMethod("bool", String.class, Component.class, boolean.class, String.class, String.class);
            } catch (NoSuchMethodException ignored) {}

            try {
                dialogInputRangeMethod = dialogInputClass.getMethod("numberRange", String.class, Component.class, float.class, float.class);
            } catch (NoSuchMethodException e1) {
                try {
                    dialogInputRangeMethod = dialogInputClass.getMethod("numberRange", String.class, Component.class, float.class, float.class, float.class);
                } catch (NoSuchMethodException e2) {
                    try {
                        dialogInputRangeMethod = dialogInputClass.getMethod("range", String.class, Component.class, float.class, float.class, float.class);
                    } catch (NoSuchMethodException ignored) {}
                }
            }

            // Build default ClickCallback.Options using public interface type
            defaultClickOptions = initClickCallbackOptions();

            // Audience.showDialog
            for (Method m : Audience.class.getMethods()) {
                if (m.getName().equals("showDialog") && m.getParameterCount() == 1) {
                    showDialogMethod = m;
                    break;
                }
            }
            if (showDialogMethod == null) {
                for (Method m : Player.class.getMethods()) {
                    if (m.getName().equals("showDialog") && m.getParameterCount() == 1) {
                        showDialogMethod = m;
                        break;
                    }
                }
            }

            initialized = (dialogCreateMethod != null && showDialogMethod != null && dialogBaseBuilderMethod != null && dialogActionCustomClickMethod != null && defaultClickOptions != null);
            methAvail = initialized;
            if (initialized) {
                statusMsg = "Paper Native Dialog API (1.21.7+) successfully bound.";
            } else {
                statusMsg = "Paper Dialog classes found but missing required reflective methods or options (options=" + (defaultClickOptions != null) + ", showDialog=" + (showDialogMethod != null) + ").";
            }
        } catch (ClassNotFoundException e) {
            clsAvail = false;
            methAvail = false;
            initialized = false;
            statusMsg = "Paper Dialog API classes not found (server is not Paper 1.21.7+).";
        } catch (Throwable t) {
            clsAvail = false;
            methAvail = false;
            initialized = false;
            Throwable root = unwrapThrowable(t);
            statusMsg = "Paper Dialog reflection failed: " + root.getClass().getSimpleName() + ": " + root.getMessage();
        }

        this.classAvailable = clsAvail;
        this.methodsAvailable = methAvail;
        this.available = initialized;
        this.initStatusMessage = statusMsg;

        if (plugin != null) {
            if (available) {
                plugin.getLogger().info("§a[IpsecuzPet] Paper Native Dialog Engine active with real reflection bindings.");
            } else if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("[DialogEngine] Native Dialog unavailable: " + initStatusMessage);
            }
        }

        if (available) {
            runSelfTestIfAvailable();
        }
    }

    public Object initClickCallbackOptions() {
        if (clickCallbackOptionsClass == null) {
            try {
                clickCallbackOptionsClass = Class.forName("net.kyori.adventure.text.event.ClickCallback$Options");
            } catch (ClassNotFoundException e) {
                recordFailure(Stage.CREATE_CALLBACK_OPTIONS, "ClickCallback.Options", "Options.class",
                        new ClassNotFoundException("net.kyori.adventure.text.event.ClickCallback$Options not found"));
                this.callbackOptionsInitialized = false;
                return null;
            }
        }
        try {
            // 1. Resolve ClickCallback.Options.builder()
            Method builderMethod = clickCallbackOptionsClass.getMethod("builder");
            // 2. Use the public return/interface type of that builder
            Class<?> builderType = builderMethod.getReturnType();
            Object builder = builderMethod.invoke(null);
            if (builder == null) {
                recordFailure(Stage.CREATE_CALLBACK_OPTIONS, "ClickCallback.Options.builder", "builder()",
                        new NullPointerException("ClickCallback.Options.builder() returned null"));
                this.callbackOptionsInitialized = false;
                return null;
            }

            // 3. Configure callback uses/lifetime if necessary (default: 1 use)
            try {
                Method usesMethod = builderType.getMethod("uses", int.class);
                usesMethod.invoke(builder, 1);
            } catch (Throwable ignored) {}

            // 4. Invoke the public build() method from the public builder interface
            Method buildMethod = builderType.getMethod("build");
            Object options = buildMethod.invoke(builder);

            // 5. Verify the returned options object is non-null and compatible with customClick
            if (options != null && clickCallbackOptionsClass.isInstance(options)) {
                this.callbackOptionsInitialized = true;
                return options;
            } else {
                recordFailure(Stage.CREATE_CALLBACK_OPTIONS, "ClickCallback.Options.Builder.build", "build()",
                        new IllegalStateException("Built options is null or not an instance of ClickCallback.Options"));
                this.callbackOptionsInitialized = false;
                return null;
            }
        } catch (Throwable t) {
            Throwable root = unwrapThrowable(t);
            recordFailure(Stage.CREATE_CALLBACK_OPTIONS, "ClickCallback.Options.builder/build", "build()", root);
            this.callbackOptionsInitialized = false;
            return null;
        }
    }

    private void runSelfTestIfAvailable() {
        if (!available) return;
        try {
            Object dummyBtn = createActionButton(Component.text("Test"), null, 100, (p, r) -> {});
            if (dummyBtn != null) {
                this.actionButtonCreationTested = true;
            }
            List<Object> btns = new ArrayList<>();
            btns.add(dummyBtn);
            Object multiActionType = null;
            if (dialogTypeMultiActionWithExitMethod != null) {
                multiActionType = dialogTypeMultiActionWithExitMethod.invoke(null, btns, null, 1);
            } else if (dialogTypeMultiActionWithExit2Method != null) {
                multiActionType = dialogTypeMultiActionWithExit2Method.invoke(null, btns, null);
            } else if (dialogTypeMultiActionMethod != null) {
                multiActionType = dialogTypeMultiActionMethod.invoke(null, btns);
            }
            if (multiActionType != null) {
                this.multiActionConstructionTested = true;
            }

            // Test empty-action guard with fallback button
            Object fallbackBtn = createActionButton(Component.text("Close"), null, 100, (p, r) -> {});
            if (fallbackBtn != null) {
                List<Object> fallbackBtns = new ArrayList<>();
                fallbackBtns.add(fallbackBtn);
                Object guardType = null;
                if (dialogTypeMultiActionWithExitMethod != null) {
                    guardType = dialogTypeMultiActionWithExitMethod.invoke(null, fallbackBtns, null, 1);
                } else if (dialogTypeMultiActionWithExit2Method != null) {
                    guardType = dialogTypeMultiActionWithExit2Method.invoke(null, fallbackBtns, null);
                } else if (dialogTypeMultiActionMethod != null) {
                    guardType = dialogTypeMultiActionMethod.invoke(null, fallbackBtns);
                }
                if (guardType != null) {
                    this.emptyActionGuardTested = true;
                }
            }
        } catch (Throwable t) {
            Throwable root = unwrapThrowable(t);
            recordFailure(lastFailureStage != null ? lastFailureStage : Stage.CONSTRUCT_MULTI_ACTION_TYPE,
                    "SelfTest", "runSelfTest", root);
            if (plugin != null) {
                plugin.getLogger().warning("[DialogEngine] Self-test failed: " + lastFailureReason);
            }
        }
    }

    public static Throwable unwrapThrowable(Throwable t) {
        if (t == null) return null;
        Throwable current = t;
        while (true) {
            if (current instanceof java.lang.reflect.InvocationTargetException ite && ite.getTargetException() != null) {
                current = ite.getTargetException();
            } else if (current instanceof java.lang.reflect.UndeclaredThrowableException ute && ute.getUndeclaredThrowable() != null) {
                current = ute.getUndeclaredThrowable();
            } else if (current.getCause() != null && current.getCause() != current) {
                current = current.getCause();
            } else {
                break;
            }
        }
        return current;
    }

    public void recordSuccess(String type, Player player) {
        this.lastAttemptType = type;
        this.lastAttemptPlayer = (player != null) ? player.getName() : "None";
        this.lastAttemptTimestamp = System.currentTimeMillis();
        this.lastAttemptSuccess = true;
        this.hasActiveFailure = false;
        this.lastFailureStage = null;
        this.lastFailureReason = null;
        this.lastRootExceptionType = null;
        this.lastRootExceptionMessage = null;
    }

    public void recordFailure(Stage stage, String methodName, String methodSignature, Throwable rootCause) {
        this.lastFailureStage = stage;
        Throwable unwrapped = unwrapThrowable(rootCause);
        String causeMsg = (unwrapped != null) ?
                unwrapped.getClass().getSimpleName() + (unwrapped.getMessage() != null ? ": " + unwrapped.getMessage() : "") : "null";
        this.lastFailureReason = "[Stage: " + (stage != null ? stage.name() : "UNKNOWN") + " in " + methodName + "] " + causeMsg;
        this.lastRootExceptionType = (unwrapped != null) ? unwrapped.getClass().getName() : "None";
        this.lastRootExceptionMessage = (unwrapped != null) ? String.valueOf(unwrapped.getMessage()) : "None";
        this.lastFailureTimestamp = System.currentTimeMillis();
        this.hasActiveFailure = true;
        this.lastAttemptSuccess = false;

        this.historicalFailureStage = stage;
        this.historicalFailureReason = this.lastFailureReason;
        this.historicalRootExceptionType = this.lastRootExceptionType;
        this.historicalRootExceptionMessage = this.lastRootExceptionMessage;
        this.historicalFailureTimestamp = this.lastFailureTimestamp;

        String key = (stage != null ? stage.name() : "UNKNOWN") + "_" + methodName;
        logRateLimited(key, "Native Dialog Failure at " + lastFailureReason + " (Sig: " + methodSignature + ")", 5000);
        if (plugin != null && plugin.getConfig().getBoolean("debug", false) && unwrapped != null) {
            plugin.getLogger().warning("[DialogEngine Debug Stack Trace]:");
            unwrapped.printStackTrace();
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isClassAvailable() {
        return classAvailable;
    }

    public boolean isMethodsAvailable() {
        return methodsAvailable;
    }

    public String getInitStatusMessage() {
        return initStatusMessage;
    }

    public Stage getLastFailureStage() {
        return lastFailureStage;
    }

    public String getLastFailureReason() {
        return lastFailureReason;
    }

    public String getLastRootExceptionType() {
        return lastRootExceptionType;
    }

    public String getLastRootExceptionMessage() {
        return lastRootExceptionMessage;
    }

    public boolean isCallbackOptionsInitialized() {
        return callbackOptionsInitialized;
    }

    public boolean isActionButtonAvailable() {
        return actionButtonCreateMethod != null || actionButtonClass != null;
    }

    public boolean isActionButtonCreationTested() {
        return actionButtonCreationTested;
    }

    public boolean isMultiActionConstructionTested() {
        return multiActionConstructionTested;
    }

    public boolean isEmptyActionGuardTested() {
        return emptyActionGuardTested;
    }

    public boolean hasActiveFailure() {
        return hasActiveFailure;
    }

    public String getLastAttemptType() {
        return lastAttemptType;
    }

    public long getLastAttemptTimestamp() {
        return lastAttemptTimestamp;
    }

    public boolean isLastAttemptSuccess() {
        return lastAttemptSuccess;
    }

    public String getLastAttemptPlayer() {
        return lastAttemptPlayer;
    }

    public long getLastFailureTimestamp() {
        return lastFailureTimestamp;
    }

    public Stage getHistoricalFailureStage() {
        return historicalFailureStage;
    }

    public String getHistoricalFailureReason() {
        return historicalFailureReason;
    }

    public String getHistoricalRootExceptionType() {
        return historicalRootExceptionType;
    }

    public String getHistoricalRootExceptionMessage() {
        return historicalRootExceptionMessage;
    }

    public long getHistoricalFailureTimestamp() {
        return historicalFailureTimestamp;
    }

    public Object getDefaultClickOptions() {
        return defaultClickOptions;
    }

    public boolean hasNoticeCapability() {
        return available && callbackOptionsInitialized && dialogTypeNoticeMethod != null && actionButtonClass != null && dialogBaseBuilderMethod != null && showDialogMethod != null;
    }

    public boolean hasConfirmationCapability() {
        return available && callbackOptionsInitialized && dialogTypeConfirmationMethod != null && actionButtonClass != null && dialogBaseBuilderMethod != null && showDialogMethod != null;
    }

    public boolean hasMultiActionCapability() {
        return available && callbackOptionsInitialized && (dialogTypeMultiActionWithExitMethod != null || dialogTypeMultiActionWithExit2Method != null || dialogTypeMultiActionMethod != null) && actionButtonClass != null && dialogBaseBuilderMethod != null && showDialogMethod != null;
    }

    public boolean hasTextCapability() {
        return available && dialogInputTextMethod != null;
    }

    public boolean hasBoolCapability() {
        return available && dialogInputBoolMethod != null;
    }

    public boolean hasRangeCapability() {
        return available && dialogInputRangeMethod != null;
    }

    public boolean hasShowDialogCapability() {
        return showDialogMethod != null;
    }

    public boolean hasResponseExtractionCapability() {
        return dialogResponseViewClass != null;
    }

    public Map<String, Boolean> auditCapabilities() {
        Map<String, Boolean> caps = new java.util.LinkedHashMap<>();
        caps.put("Notice", hasNoticeCapability());
        caps.put("Confirmation", hasConfirmationCapability());
        caps.put("MultiAction", hasMultiActionCapability());
        caps.put("TextInput", hasTextCapability());
        caps.put("BoolInput", hasBoolCapability());
        caps.put("RangeInput", hasRangeCapability());
        caps.put("ShowDialog", hasShowDialogCapability());
        caps.put("ResponseExtraction", hasResponseExtractionCapability());
        caps.put("CallbackOptions", callbackOptionsInitialized);
        caps.put("ActionButton", isActionButtonAvailable());
        return caps;
    }

    public String getCapabilitySummary() {
        StringBuilder sb = new StringBuilder();
        auditCapabilities().forEach((k, v) -> {
            if (sb.length() > 0) sb.append(", ");
            sb.append(k).append(": ").append(v ? "YES" : "NO");
        });
        return sb.toString();
    }

    public void logRateLimited(String key, String message, long intervalMillis) {
        long now = System.currentTimeMillis();
        Long last = logRateLimitMap.get(key);
        if (last == null || now - last >= intervalMillis) {
            logRateLimitMap.put(key, now);
            if (plugin != null) {
                plugin.getLogger().warning("[DialogEngine] " + message);
            }
        }
    }

    public static Player extractPlayerFromAudience(Object audience) {
        if (audience instanceof Player p) {
            return p;
        }
        if (audience instanceof net.kyori.adventure.audience.ForwardingAudience.Single single) {
            Audience target = single.audience();
            if (target instanceof Player p) return p;
        }
        if (audience != null) {
            try {
                Method m = audience.getClass().getMethod("getUniqueId");
                Object uuid = m.invoke(audience);
                if (uuid instanceof UUID u) {
                    return org.bukkit.Bukkit.getPlayer(u);
                }
            } catch (Throwable ignored) {}
            try {
                Method m = audience.getClass().getMethod("getName");
                Object name = m.invoke(audience);
                if (name instanceof String s) {
                    return org.bukkit.Bukkit.getPlayer(s);
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private NativeDialogResponse createResponseProxy(Object responseView) {
        return new NativeDialogResponse() {
            @Override
            public String getText(String key) {
                if (responseView == null || key == null || dialogResponseViewClass == null) return null;
                try {
                    Method m = dialogResponseViewClass.getMethod("getText", String.class);
                    return (String) m.invoke(responseView, key);
                } catch (Throwable ignored) {
                    return null;
                }
            }

            @Override
            public Boolean getBoolean(String key) {
                if (responseView == null || key == null || dialogResponseViewClass == null) return null;
                try {
                    Method m = dialogResponseViewClass.getMethod("getBoolean", String.class);
                    return (Boolean) m.invoke(responseView, key);
                } catch (Throwable ignored) {
                    return null;
                }
            }

            @Override
            public Float getFloat(String key) {
                if (responseView == null || key == null || dialogResponseViewClass == null) return null;
                try {
                    Method m = dialogResponseViewClass.getMethod("getFloat", String.class);
                    return (Float) m.invoke(responseView, key);
                } catch (Throwable ignored) {
                    return null;
                }
            }
        };
    }

    public Object createActionButton(Component label, Component tooltip, int width, NativeDialogActionHandler handler) throws Exception {
        Object actionProxy = null;
        if (handler != null) {
            if (dialogActionCallbackClass == null || dialogActionCustomClickMethod == null || defaultClickOptions == null) {
                IllegalStateException ex = new IllegalStateException("Callback API or ClickCallback.Options unavailable (defaultClickOptions=" + defaultClickOptions + ")");
                recordFailure(Stage.CREATE_BUTTON_ACTION, "DialogAction.customClick", "customClick(callback, options)", ex);
                throw ex;
            }

            AtomicBoolean executed = new AtomicBoolean(false);

            Object callback = Proxy.newProxyInstance(
                    dialogActionCallbackClass.getClassLoader(),
                    new Class<?>[]{ dialogActionCallbackClass },
                    (proxy, method, args) -> {
                        if (method.getName().equals("accept") && args != null && args.length == 2) {
                            if (!executed.compareAndSet(false, true)) {
                                // Already executed once, ignore duplicate callback execution
                                return null;
                            }
                            Object responseView = args[0];
                            Object audience = args[1];
                            Player player = extractPlayerFromAudience(audience);
                            if (player != null) {
                                NativeDialogResponse response = createResponseProxy(responseView);
                                SchedulerUtils.runEntityTask(plugin, player, () -> handler.handle(player, response));
                            }
                            return null;
                        }
                        if (method.getName().equals("equals")) return proxy == args[0];
                        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        if (method.getName().equals("toString")) return "PaperNativeDialogActionCallback";
                        return null;
                    }
            );

            try {
                actionProxy = dialogActionCustomClickMethod.invoke(null, callback, defaultClickOptions);
                if (actionProxy == null) {
                    throw new NullPointerException("DialogAction.customClick returned null");
                }
            } catch (Throwable t) {
                Throwable root = unwrapThrowable(t);
                recordFailure(Stage.CREATE_BUTTON_ACTION, "DialogAction.customClick", "customClick(callback, options)", root);
                throw new RuntimeException("Failed to create button action proxy: " + root.getMessage(), root);
            }
        }

        try {
            if (actionButtonCreateMethod != null) {
                Object btn = actionButtonCreateMethod.invoke(null, label, tooltip, width, actionProxy);
                if (btn == null) throw new NullPointerException("ActionButton.create returned null");
                return btn;
            } else {
                Method bMethod = actionButtonClass.getMethod("builder", Component.class);
                Class<?> builderType = bMethod.getReturnType();
                Object builder = bMethod.invoke(null, label);
                if (tooltip != null) {
                    try {
                        Method m = builderType.getMethod("tooltip", Component.class);
                        m.invoke(builder, tooltip);
                    } catch (Throwable ignored) {}
                }
                try {
                    Method m = builderType.getMethod("width", int.class);
                    m.invoke(builder, width);
                } catch (Throwable ignored) {}
                if (actionProxy != null) {
                    try {
                        Method m = builderType.getMethod("action", dialogActionClass);
                        m.invoke(builder, actionProxy);
                    } catch (Throwable ignored) {}
                }
                Method buildM = builderType.getMethod("build");
                Object btn = buildM.invoke(builder);
                if (btn == null) throw new NullPointerException("ActionButton.Builder.build returned null");
                return btn;
            }
        } catch (Throwable t) {
            Throwable root = unwrapThrowable(t);
            recordFailure(Stage.CONSTRUCT_ACTION_BUTTON, "ActionButton.create", "create(label, tooltip, width, action)", root);
            throw new RuntimeException("Failed to construct ActionButton: " + root.getMessage(), root);
        }
    }

    public Object createTextInput(String key, Component label, int width, int maxLength, String initialValue) throws Exception {
        if (dialogInputTextMethod == null) return null;
        Object builder = dialogInputTextMethod.invoke(null, key, label);
        if (builder == null) return null;
        Class<?> builderType = dialogInputTextMethod.getReturnType();
        try {
            Method m = builderType.getMethod("width", int.class);
            m.invoke(builder, width);
        } catch (Throwable ignored) {}
        try {
            Method m = builderType.getMethod("maxLength", int.class);
            m.invoke(builder, maxLength);
        } catch (Throwable ignored) {}
        if (initialValue != null) {
            try {
                Method m = builderType.getMethod("initial", String.class);
                m.invoke(builder, initialValue);
            } catch (Throwable ignored) {}
        }
        try {
            Method buildM = builderType.getMethod("build");
            return buildM.invoke(builder);
        } catch (Throwable ignored) {
            return builder;
        }
    }

    public Object createBoolInput(String key, Component label, boolean initial, String onTrue, String onFalse) throws Exception {
        if (dialogInputBoolMethod == null) return null;
        return dialogInputBoolMethod.invoke(null, key, label, initial, onTrue, onFalse);
    }

    public Object createRangeInput(String key, Component label, float start, float end, float step, float initial, int width, String labelFormat) throws Exception {
        return createNumberRangeInput(key, label, start, end, step, initial, width, labelFormat);
    }

    public Object createNumberRangeInput(String key, Component label, float start, float end, float step, float initial, int width, String labelFormat) throws Exception {
        if (dialogInputRangeMethod == null) return null;
        Object builder;
        if (dialogInputRangeMethod.getParameterCount() == 4) {
            builder = dialogInputRangeMethod.invoke(null, key, label, start, end);
        } else if (dialogInputRangeMethod.getParameterCount() == 5) {
            builder = dialogInputRangeMethod.invoke(null, key, label, start, end, step);
        } else {
            builder = dialogInputRangeMethod.invoke(null, key, label, start, end, step);
        }
        if (builder == null) return null;
        Class<?> builderType = dialogInputRangeMethod.getReturnType();

        try {
            Method m = builderType.getMethod("step", float.class);
            m.invoke(builder, step);
        } catch (Throwable ignored) {}

        try {
            Method m = builderType.getMethod("initial", float.class);
            m.invoke(builder, initial);
        } catch (Throwable ignored) {}

        if (width > 0) {
            try {
                Method m = builderType.getMethod("width", int.class);
                m.invoke(builder, width);
            } catch (Throwable ignored) {}
        }

        if (labelFormat != null) {
            try {
                Method m = builderType.getMethod("labelFormat", String.class);
                m.invoke(builder, labelFormat);
            } catch (Throwable e) {
                try {
                    Method m = builderType.getMethod("format", String.class);
                    m.invoke(builder, labelFormat);
                } catch (Throwable ignored) {}
            }
        }

        try {
            Method buildM = builderType.getMethod("build");
            return buildM.invoke(builder);
        } catch (NoSuchMethodException e) {
            return builder;
        }
    }

    public Object buildDialog(Component title, List<Component> bodyLines, List<Object> inputs, Object dialogType) throws Exception {
        Object dialogBase;
        try {
            List<Object> bodies = new ArrayList<>();
            if (bodyLines != null) {
                for (Component line : bodyLines) {
                    bodies.add(dialogBodyPlainMessageMethod.invoke(null, line));
                }
            }

            Object baseBuilder = dialogBaseBuilderMethod.invoke(null, title);
            try {
                Method m = dialogBaseBuilderClass.getMethod("canCloseWithEscape", boolean.class);
                m.invoke(baseBuilder, true);
            } catch (Throwable ignored) {}

            try {
                Method m = dialogBaseBuilderClass.getMethod("body", List.class);
                m.invoke(baseBuilder, bodies);
            } catch (Throwable ignored) {}

            if (inputs != null && !inputs.isEmpty()) {
                try {
                    Method m = dialogBaseBuilderClass.getMethod("inputs", List.class);
                    m.invoke(baseBuilder, inputs);
                } catch (Throwable ignored) {}
            }

            Method buildM = dialogBaseBuilderClass.getMethod("build");
            dialogBase = buildM.invoke(baseBuilder);
            if (dialogBase == null) {
                throw new NullPointerException("DialogBase.build() returned null");
            }
        } catch (Throwable t) {
            Throwable root = unwrapThrowable(t);
            recordFailure(Stage.BUILD_DIALOG_BASE, "DialogBase.Builder.build", "build()", root);
            throw new RuntimeException("Failed in BUILD_DIALOG_BASE: " + root.getMessage(), root);
        }

        Consumer<?> consumer = (factory) -> {
            try {
                Method emptyMethod = registryBuilderFactoryClass.getMethod("empty");
                Object entryBuilder = emptyMethod.invoke(factory);
                Method baseMethod = dialogRegistryEntryBuilderClass.getMethod("base", dialogBaseClass);
                baseMethod.invoke(entryBuilder, dialogBase);
                Method typeMethod = dialogRegistryEntryBuilderClass.getMethod("type", dialogTypeClass);
                typeMethod.invoke(entryBuilder, dialogType);
            } catch (Throwable t) {
                Throwable root = unwrapThrowable(t);
                recordFailure(Stage.BUILD_REGISTRY_ENTRY, "DialogRegistryEntry.Builder", "base(DialogBase)/type(DialogType)", root);
                throw new RuntimeException("Failed in BUILD_REGISTRY_ENTRY: " + root.getMessage(), root);
            }
        };

        try {
            Object dialogInstance = dialogCreateMethod.invoke(null, consumer);
            if (dialogInstance == null) {
                NullPointerException npe = new NullPointerException("Dialog.create returned null");
                recordFailure(Stage.CREATE_NATIVE_DIALOG, "Dialog.create", "create(Consumer)", npe);
                throw npe;
            }
            return dialogInstance;
        } catch (Throwable t) {
            Throwable root = unwrapThrowable(t);
            recordFailure(Stage.CREATE_NATIVE_DIALOG, "Dialog.create", "create(Consumer)", root);
            throw new RuntimeException("Failed in CREATE_NATIVE_DIALOG: " + root.getMessage(), root);
        }
    }

    public boolean showDialog(Player player, Object dialogInstance) {
        if (!available || player == null || dialogInstance == null) {
            IllegalArgumentException ex = new IllegalArgumentException("Engine unavailable (" + available + ") or player/dialog null");
            recordFailure(Stage.INVOKE_SHOW_DIALOG, "Audience.showDialog", "showDialog(Dialog)", ex);
            return false;
        }
        try {
            showDialogMethod.invoke(player, dialogInstance);
            return true;
        } catch (Throwable t) {
            Throwable root = unwrapThrowable(t);
            recordFailure(Stage.INVOKE_SHOW_DIALOG, "Audience.showDialog", "showDialog(Dialog)", root);
            if (plugin != null && plugin.getConfig().getBoolean("debug", false)) {
                logRateLimited("showDialog_" + player.getUniqueId(),
                        "Failed to show native Dialog to " + player.getName() + " [Stage 8]: " + root.getClass().getSimpleName() + ": " + root.getMessage(), 5000);
            }
            return false;
        }
    }

    public boolean showNotice(Player player, Component title, List<Component> bodyLines, Component buttonText, Runnable onClose) {
        this.lastAttemptType = "Notice";
        this.lastAttemptPlayer = (player != null) ? player.getName() : "None";
        this.lastAttemptTimestamp = System.currentTimeMillis();

        if (!available) {
            recordFailure(Stage.CREATE_CALLBACK_OPTIONS, "PaperNativeDialogEngine", "isAvailable",
                    new IllegalStateException("Engine unavailable"));
            return false;
        }
        try {
            Object btn = createActionButton(buttonText, null, 140, (p, resp) -> {
                if (onClose != null) onClose.run();
            });
            Object noticeType;
            try {
                noticeType = dialogTypeNoticeMethod.invoke(null, btn);
            } catch (Throwable t) {
                Throwable root = unwrapThrowable(t);
                recordFailure(Stage.CONSTRUCT_MULTI_ACTION_TYPE, "DialogType.notice", "notice(ActionButton)", root);
                return false;
            }
            Object dialog = buildDialog(title, bodyLines, null, noticeType);
            boolean shown = showDialog(player, dialog);
            if (shown) {
                recordSuccess("Notice", player);
            }
            return shown;
        } catch (Throwable t) {
            Throwable root = unwrapThrowable(t);
            if (lastFailureStage == null) {
                recordFailure(Stage.CONSTRUCT_MULTI_ACTION_TYPE, "showNotice", "orchestration", root);
            }
            return false;
        }
    }

    public boolean showConfirmation(Player player, Component title, List<Component> bodyLines, Component confirmText, Component cancelText, Consumer<Boolean> callback) {
        this.lastAttemptType = "Confirmation";
        this.lastAttemptPlayer = (player != null) ? player.getName() : "None";
        this.lastAttemptTimestamp = System.currentTimeMillis();

        if (!available) {
            recordFailure(Stage.CREATE_CALLBACK_OPTIONS, "PaperNativeDialogEngine", "isAvailable",
                    new IllegalStateException("Engine unavailable"));
            return false;
        }
        try {
            Object yesBtn = createActionButton(confirmText, null, 120, (p, resp) -> {
                if (callback != null) callback.accept(true);
            });
            Object noBtn = createActionButton(cancelText, null, 120, (p, resp) -> {
                if (callback != null) callback.accept(false);
            });
            Object confirmType;
            try {
                confirmType = dialogTypeConfirmationMethod.invoke(null, yesBtn, noBtn);
            } catch (Throwable t) {
                Throwable root = unwrapThrowable(t);
                recordFailure(Stage.CONSTRUCT_MULTI_ACTION_TYPE, "DialogType.confirmation", "confirmation(ActionButton, ActionButton)", root);
                return false;
            }
            Object dialog = buildDialog(title, bodyLines, null, confirmType);
            boolean shown = showDialog(player, dialog);
            if (shown) {
                recordSuccess("Confirmation", player);
            }
            return shown;
        } catch (Throwable t) {
            Throwable root = unwrapThrowable(t);
            if (lastFailureStage == null) {
                recordFailure(Stage.CONSTRUCT_MULTI_ACTION_TYPE, "showConfirmation", "orchestration", root);
            }
            return false;
        }
    }

    public boolean showMultiAction(Player player, Component title, List<Component> bodyLines, List<Object> inputs, List<NativeButton> buttons, NativeButton exitButton, int columns) {
        this.lastAttemptType = "MultiAction";
        this.lastAttemptPlayer = (player != null) ? player.getName() : "None";
        this.lastAttemptTimestamp = System.currentTimeMillis();

        if (!available) {
            recordFailure(Stage.CREATE_CALLBACK_OPTIONS, "PaperNativeDialogEngine", "isAvailable",
                    new IllegalStateException("Engine unavailable: " + initStatusMessage));
            return false;
        }
        if (defaultClickOptions == null) {
            recordFailure(Stage.CREATE_CALLBACK_OPTIONS, "ClickCallback.Options", "defaultClickOptions",
                    new IllegalStateException("ClickCallback.Options not initialized"));
            return false;
        }

        try {
            List<Object> actionButtons = new ArrayList<>();
            if (buttons != null) {
                for (NativeButton b : buttons) {
                    if (b == null) continue;
                    try {
                        Object actBtn = createActionButton(b.label(), b.tooltip(), b.width(), b.handler());
                        if (actBtn != null) {
                            actionButtons.add(actBtn);
                        }
                    } catch (Throwable t) {
                        Throwable root = unwrapThrowable(t);
                        recordFailure(Stage.CONSTRUCT_ACTION_BUTTON, "createActionButton", "ActionButton.create", root);
                    }
                }
            }
            Object exitBtn = null;
            if (exitButton != null) {
                try {
                    exitBtn = createActionButton(exitButton.label(), exitButton.tooltip(), exitButton.width(), exitButton.handler());
                } catch (Throwable t) {
                    Throwable root = unwrapThrowable(t);
                    recordFailure(Stage.CONSTRUCT_ACTION_BUTTON, "createActionButton", "ActionButton.create(exit)", root);
                }
            }

            // Requirement 4: If main action list is empty but exit button is valid, promote exit button
            if (actionButtons.isEmpty() && exitBtn != null) {
                actionButtons.add(exitBtn);
                exitBtn = null;
                this.emptyActionGuardTested = true;
            }

            // Requirement 6: If neither normal action nor exit action exists, produce safe fallback button
            if (actionButtons.isEmpty()) {
                Component fallbackLabel = Component.text("Close");
                if (plugin != null && plugin.getLanguage() != null) {
                    try {
                        String lbl = plugin.getLanguage().getMessageOrDefault("dialog.btn_close", "&c✖ Đóng");
                        fallbackLabel = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(lbl);
                    } catch (Throwable ignored) {}
                }
                try {
                    Object fallbackBtn = createActionButton(fallbackLabel, null, 100, (p, resp) -> {});
                    if (fallbackBtn != null) {
                        actionButtons.add(fallbackBtn);
                        this.emptyActionGuardTested = true;
                    }
                } catch (Throwable t) {
                    Throwable root = unwrapThrowable(t);
                    recordFailure(Stage.CONSTRUCT_ACTION_BUTTON, "createActionButton", "fallbackButton", root);
                }
            }

            // Requirement 7: Never invoke multiAction with an empty action list
            if (actionButtons.isEmpty()) {
                IllegalArgumentException ex = new IllegalArgumentException("actions cannot be empty: no normal actions, exit button, or fallback button could be constructed");
                recordFailure(Stage.CONSTRUCT_MULTI_ACTION_TYPE, "DialogType.multiAction", "actions.isEmpty", ex);
                return false;
            }

            Object multiActionType;
            try {
                if (dialogTypeMultiActionWithExitMethod != null) {
                    multiActionType = dialogTypeMultiActionWithExitMethod.invoke(null, actionButtons, exitBtn, Math.max(1, columns));
                } else if (dialogTypeMultiActionWithExit2Method != null) {
                    multiActionType = dialogTypeMultiActionWithExit2Method.invoke(null, actionButtons, exitBtn);
                } else if (dialogTypeMultiActionMethod != null) {
                    if (exitBtn != null) actionButtons.add(exitBtn);
                    multiActionType = dialogTypeMultiActionMethod.invoke(null, actionButtons);
                } else {
                    NoSuchMethodException ex = new NoSuchMethodException("No multiAction method found on DialogType");
                    recordFailure(Stage.CONSTRUCT_MULTI_ACTION_TYPE, "DialogType.multiAction", "multiAction", ex);
                    return false;
                }
            } catch (Throwable t) {
                Throwable root = unwrapThrowable(t);
                recordFailure(Stage.CONSTRUCT_MULTI_ACTION_TYPE, "DialogType.multiAction", "multiAction", root);
                return false;
            }

            Object dialog = buildDialog(title, bodyLines, inputs, multiActionType);
            boolean shown = showDialog(player, dialog);
            if (shown) {
                recordSuccess("MultiAction", player);
                this.multiActionConstructionTested = true;
            }
            return shown;
        } catch (Throwable t) {
            Throwable root = unwrapThrowable(t);
            if (lastFailureStage == null) {
                recordFailure(Stage.CONSTRUCT_MULTI_ACTION_TYPE, "showMultiAction", "orchestration", root);
            }
            return false;
        }
    }
}
