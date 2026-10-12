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
import java.util.function.Consumer;

/**
 * Isolated PaperMC Native Dialog Engine implementing Paper 1.21.7+ Dialog API via safe runtime reflection.
 * Ensures strict zero class-loading errors on older versions while providing 100% genuine Dialog objects,
 * dynamic inputs, action buttons, and callback dispatch on compatible runtimes.
 */
public class PaperNativeDialogEngine {

    private final IpsecuzPet plugin;
    private final boolean available;
    private final String initStatusMessage;
    private final boolean classAvailable;
    private final boolean methodsAvailable;
    private volatile String lastFailureReason;

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
    private Method dialogInputTextMethod;
    private Method dialogInputBoolMethod;
    private Method dialogInputRangeMethod;
    private Method showDialogMethod;
    private Object defaultClickOptions;
    private final java.util.concurrent.ConcurrentHashMap<String, Long> logRateLimitMap = new java.util.concurrent.ConcurrentHashMap<>();

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
            dialogBaseBuilderMethod = dialogBaseClass.getMethod("builder", Component.class);
            dialogBodyPlainMessageMethod = dialogBodyClass.getMethod("plainMessage", Component.class);

            try {
                actionButtonCreateMethod = actionButtonClass.getMethod("create", Component.class, Component.class, int.class, dialogActionClass);
            } catch (NoSuchMethodException ignored) {}

            dialogActionCustomClickMethod = dialogActionClass.getMethod("customClick", dialogActionCallbackClass, clickCallbackOptionsClass);

            dialogTypeNoticeMethod = dialogTypeClass.getMethod("notice", actionButtonClass);
            dialogTypeConfirmationMethod = dialogTypeClass.getMethod("confirmation", actionButtonClass, actionButtonClass);

            try {
                dialogTypeMultiActionWithExitMethod = dialogTypeClass.getMethod("multiAction", List.class, actionButtonClass, int.class);
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

            // Build default ClickCallback.Options
            try {
                Method builderMethod = clickCallbackOptionsClass.getMethod("builder");
                Object builder = builderMethod.invoke(null);
                defaultClickOptions = builder.getClass().getMethod("build").invoke(builder);
            } catch (Throwable ignored) {
                try {
                    defaultClickOptions = clickCallbackOptionsClass.getMethod("defaultOptions").invoke(null);
                } catch (Throwable ignored2) {}
            }

            // Audience.showDialog
            for (Method m : Audience.class.getMethods()) {
                if (m.getName().equals("showDialog") && m.getParameterCount() == 1) {
                    showDialogMethod = m;
                    break;
                }
            }

            initialized = (dialogCreateMethod != null && showDialogMethod != null && dialogBaseBuilderMethod != null && dialogActionCustomClickMethod != null);
            methAvail = initialized;
            if (initialized) {
                statusMsg = "Paper Native Dialog API (1.21.7+) successfully bound.";
            } else {
                statusMsg = "Paper Dialog classes found but missing required reflective methods (showDialog=" + (showDialogMethod != null) + ").";
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
            statusMsg = "Paper Dialog reflection failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
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

    public String getLastFailureReason() {
        return lastFailureReason;
    }

    public boolean hasNoticeCapability() {
        return available && dialogTypeNoticeMethod != null && actionButtonClass != null && dialogBaseBuilderMethod != null && showDialogMethod != null;
    }

    public boolean hasConfirmationCapability() {
        return available && dialogTypeConfirmationMethod != null && actionButtonClass != null && dialogBaseBuilderMethod != null && showDialogMethod != null;
    }

    public boolean hasMultiActionCapability() {
        return available && (dialogTypeMultiActionWithExitMethod != null || dialogTypeMultiActionMethod != null) && actionButtonClass != null && dialogBaseBuilderMethod != null && showDialogMethod != null;
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

    public java.util.Map<String, Boolean> auditCapabilities() {
        java.util.Map<String, Boolean> caps = new java.util.LinkedHashMap<>();
        caps.put("Notice", hasNoticeCapability());
        caps.put("Confirmation", hasConfirmationCapability());
        caps.put("MultiAction", hasMultiActionCapability());
        caps.put("TextInput", hasTextCapability());
        caps.put("BoolInput", hasBoolCapability());
        caps.put("RangeInput", hasRangeCapability());
        caps.put("ShowDialog", hasShowDialogCapability());
        caps.put("ResponseExtraction", hasResponseExtractionCapability());
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

    public Object createActionButton(Component label, Component tooltip, int width, NativeDialogActionHandler handler) throws Exception {
        Object actionProxy = null;
        if (handler != null && dialogActionCallbackClass != null) {
            Object callback = Proxy.newProxyInstance(
                    dialogActionCallbackClass.getClassLoader(),
                    new Class<?>[]{ dialogActionCallbackClass },
                    (proxy, method, args) -> {
                        if (method.getName().equals("accept") && args.length == 2) {
                            Object responseView = args[0];
                            Object audience = args[1];
                            Player player = (audience instanceof Player p) ? p : null;
                            if (player != null) {
                                NativeDialogResponse response = new NativeDialogResponse() {
                                    @Override
                                    public String getText(String key) {
                                        if (responseView == null || key == null) return null;
                                        try {
                                            Method m = dialogResponseViewClass.getMethod("getText", String.class);
                                            return (String) m.invoke(responseView, key);
                                        } catch (Throwable ignored) {
                                            return null;
                                        }
                                    }

                                    @Override
                                    public Boolean getBoolean(String key) {
                                        if (responseView == null || key == null) return null;
                                        try {
                                            Method m = dialogResponseViewClass.getMethod("getBoolean", String.class);
                                            return (Boolean) m.invoke(responseView, key);
                                        } catch (Throwable ignored) {
                                            return null;
                                        }
                                    }

                                    @Override
                                    public Float getFloat(String key) {
                                        if (responseView == null || key == null) return null;
                                        try {
                                            Method m = dialogResponseViewClass.getMethod("getFloat", String.class);
                                            return (Float) m.invoke(responseView, key);
                                        } catch (Throwable ignored) {
                                            return null;
                                        }
                                    }
                                };
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
            actionProxy = dialogActionCustomClickMethod.invoke(null, callback, defaultClickOptions);
        }

        if (actionButtonCreateMethod != null) {
            return actionButtonCreateMethod.invoke(null, label, tooltip, width, actionProxy);
        } else {
            Method bMethod = actionButtonClass.getMethod("builder", Component.class);
            Object builder = bMethod.invoke(null, label);
            if (tooltip != null) {
                builder.getClass().getMethod("tooltip", Component.class).invoke(builder, tooltip);
            }
            builder.getClass().getMethod("width", int.class).invoke(builder, width);
            if (actionProxy != null) {
                builder.getClass().getMethod("action", dialogActionClass).invoke(builder, actionProxy);
            }
            return builder.getClass().getMethod("build").invoke(builder);
        }
    }

    public Object createTextInput(String key, Component label, int width, int maxLength, String initialValue) throws Exception {
        if (dialogInputTextMethod == null) return null;
        Object builder = dialogInputTextMethod.invoke(null, key, label);
        try {
            builder.getClass().getMethod("width", int.class).invoke(builder, width);
        } catch (Throwable ignored) {}
        try {
            builder.getClass().getMethod("maxLength", int.class).invoke(builder, maxLength);
        } catch (Throwable ignored) {}
        if (initialValue != null) {
            try {
                builder.getClass().getMethod("initial", String.class).invoke(builder, initialValue);
            } catch (Throwable ignored) {}
        }
        return builder.getClass().getMethod("build").invoke(builder);
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

        try {
            Method m = builder.getClass().getMethod("step", float.class);
            m.invoke(builder, step);
        } catch (Throwable ignored) {}

        try {
            Method m = builder.getClass().getMethod("initial", float.class);
            m.invoke(builder, initial);
        } catch (Throwable ignored) {}

        if (width > 0) {
            try {
                Method m = builder.getClass().getMethod("width", int.class);
                m.invoke(builder, width);
            } catch (Throwable ignored) {}
        }

        if (labelFormat != null) {
            try {
                Method m = builder.getClass().getMethod("labelFormat", String.class);
                m.invoke(builder, labelFormat);
            } catch (Throwable e) {
                try {
                    Method m = builder.getClass().getMethod("format", String.class);
                    m.invoke(builder, labelFormat);
                } catch (Throwable ignored) {}
            }
        }

        try {
            Method buildM = builder.getClass().getMethod("build");
            return buildM.invoke(builder);
        } catch (NoSuchMethodException e) {
            return builder;
        }
    }

    public Object buildDialog(Component title, List<Component> bodyLines, List<Object> inputs, Object dialogType) throws Exception {
        List<Object> bodies = new ArrayList<>();
        if (bodyLines != null) {
            for (Component line : bodyLines) {
                bodies.add(dialogBodyPlainMessageMethod.invoke(null, line));
            }
        }

        Object baseBuilder = dialogBaseBuilderMethod.invoke(null, title);
        dialogBaseBuilderClass.getMethod("canCloseWithEscape", boolean.class).invoke(baseBuilder, true);
        dialogBaseBuilderClass.getMethod("body", List.class).invoke(baseBuilder, bodies);

        if (inputs != null && !inputs.isEmpty()) {
            dialogBaseBuilderClass.getMethod("inputs", List.class).invoke(baseBuilder, inputs);
        }

        Object dialogBase = dialogBaseBuilderClass.getMethod("build").invoke(baseBuilder);

        Consumer<?> consumer = (factory) -> {
            try {
                Method emptyMethod = registryBuilderFactoryClass.getMethod("empty");
                Object entryBuilder = emptyMethod.invoke(factory);
                Method baseMethod = dialogRegistryEntryBuilderClass.getMethod("base", dialogBaseClass);
                baseMethod.invoke(entryBuilder, dialogBase);
                Method typeMethod = dialogRegistryEntryBuilderClass.getMethod("type", dialogTypeClass);
                typeMethod.invoke(entryBuilder, dialogType);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        return dialogCreateMethod.invoke(null, consumer);
    }

    public boolean showDialog(Player player, Object dialogInstance) {
        if (!available || player == null || dialogInstance == null) {
            lastFailureReason = "Engine not available or player/dialog null";
            return false;
        }
        try {
            showDialogMethod.invoke(player, dialogInstance);
            return true;
        } catch (Throwable t) {
            lastFailureReason = "showDialog invocation: " + t.getClass().getSimpleName() + ": " + t.getMessage();
            if (plugin != null && plugin.getConfig().getBoolean("debug", false)) {
                logRateLimited("showDialog_" + player.getUniqueId(), "Failed to show native Dialog to " + player.getName() + ": " + t.getMessage(), 5000);
            }
            return false;
        }
    }

    public boolean showNotice(Player player, Component title, List<Component> bodyLines, Component buttonText, Runnable onClose) {
        if (!available) {
            lastFailureReason = "Engine not available";
            return false;
        }
        try {
            Object btn = createActionButton(buttonText, null, 140, (p, resp) -> {
                if (onClose != null) onClose.run();
            });
            Object noticeType = dialogTypeNoticeMethod.invoke(null, btn);
            Object dialog = buildDialog(title, bodyLines, null, noticeType);
            return showDialog(player, dialog);
        } catch (Throwable t) {
            lastFailureReason = "showNotice: " + t.getClass().getSimpleName() + ": " + t.getMessage();
            return false;
        }
    }

    public boolean showConfirmation(Player player, Component title, List<Component> bodyLines, Component confirmText, Component cancelText, Consumer<Boolean> callback) {
        if (!available) {
            lastFailureReason = "Engine not available";
            return false;
        }
        try {
            Object yesBtn = createActionButton(confirmText, null, 120, (p, resp) -> {
                if (callback != null) callback.accept(true);
            });
            Object noBtn = createActionButton(cancelText, null, 120, (p, resp) -> {
                if (callback != null) callback.accept(false);
            });
            Object confirmType = dialogTypeConfirmationMethod.invoke(null, yesBtn, noBtn);
            Object dialog = buildDialog(title, bodyLines, null, confirmType);
            return showDialog(player, dialog);
        } catch (Throwable t) {
            lastFailureReason = "showConfirmation: " + t.getClass().getSimpleName() + ": " + t.getMessage();
            return false;
        }
    }

    public boolean showMultiAction(Player player, Component title, List<Component> bodyLines, List<Object> inputs, List<NativeButton> buttons, NativeButton exitButton, int columns) {
        if (!available) {
            lastFailureReason = "Engine not available";
            return false;
        }
        try {
            List<Object> actionButtons = new ArrayList<>();
            if (buttons != null) {
                for (NativeButton b : buttons) {
                    actionButtons.add(createActionButton(b.label(), b.tooltip(), b.width(), b.handler()));
                }
            }
            Object exitBtn = null;
            if (exitButton != null) {
                exitBtn = createActionButton(exitButton.label(), exitButton.tooltip(), exitButton.width(), exitButton.handler());
            }

            Object multiActionType;
            if (dialogTypeMultiActionWithExitMethod != null) {
                multiActionType = dialogTypeMultiActionWithExitMethod.invoke(null, actionButtons, exitBtn, Math.max(1, columns));
            } else if (dialogTypeMultiActionMethod != null) {
                if (exitBtn != null) actionButtons.add(exitBtn);
                multiActionType = dialogTypeMultiActionMethod.invoke(null, actionButtons);
            } else {
                lastFailureReason = "No multiAction method available on DialogType";
                return false;
            }

            Object dialog = buildDialog(title, bodyLines, inputs, multiActionType);
            return showDialog(player, dialog);
        } catch (Throwable t) {
            lastFailureReason = "showMultiAction: " + t.getClass().getSimpleName() + ": " + t.getMessage();
            return false;
        }
    }
}

