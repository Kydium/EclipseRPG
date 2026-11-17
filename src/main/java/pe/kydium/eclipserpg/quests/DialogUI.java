package pe.kydium.eclipserpg.quests;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.util.Txt;

import java.util.ArrayList;
import java.util.List;

/** Ventanas de diálogo nativas de Minecraft (Dialog API de Paper). */
@SuppressWarnings("UnstableApiUsage")
public final class DialogUI {

    /** Un botón de la ventana: texto, descripción y qué hace al pulsarlo. */
    public record Button(String label, String tooltip, Runnable action) {}

    private final EclipseRPG plugin;

    public DialogUI(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("dialogos.ventana", true);
    }

    /** Muestra una ventana con el NPC, sus líneas de texto y botones. */
    public void show(Player p, String speaker, List<String> lines, List<Button> buttons, boolean canEscape) {
        List<DialogBody> body = new ArrayList<>();
        for (String l : lines) body.add(DialogBody.plainMessage(Txt.mm("<white>" + l)));

        List<ActionButton> actions = new ArrayList<>();
        // respuestas largas: botones más anchos, uno debajo del otro
        int longest = 0;
        for (Button b : buttons) longest = Math.max(longest, b.label().replaceAll("<[^>]+>", "").length());
        int width = longest > 28 ? Math.min(400, 60 + longest * 6) : 200;
        boolean stack = longest > 28;
        for (Button b : buttons) {
            DialogAction action = b.action() == null ? null : DialogAction.customClick(
                    (view, audience) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (p.isOnline()) b.action().run();
                    }),
                    ClickCallback.Options.builder().uses(1).build());
            actions.add(ActionButton.create(Txt.mm(b.label()),
                    b.tooltip() == null ? null : Txt.mm("<gray>" + b.tooltip()), width, action));
        }

        DialogBase base = DialogBase.builder(Txt.mm(speaker))
                .canCloseWithEscape(canEscape)
                .body(body)
                .build();

        DialogType type;
        if (actions.size() == 1) {
            type = DialogType.notice(actions.get(0));
        } else if (actions.size() == 2 && !stack) {
            type = DialogType.confirmation(actions.get(0), actions.get(1));
        } else {
            type = DialogType.multiAction(actions).columns(1).build();
        }
        final DialogType finalType = type;
        Dialog dialog = Dialog.create(b -> b.empty().base(base).type(finalType));
        p.showDialog(dialog);
    }

    public static Component title(String s) {
        return Txt.mm(s);
    }
}
