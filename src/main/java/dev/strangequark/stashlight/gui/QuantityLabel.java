package dev.strangequark.stashlight.gui;


import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.network.chat.Component;

public class QuantityLabel extends LabelComponent {
    private float scale = 1f;

    public QuantityLabel(Component text) {
        super(text);
    }

    public static QuantityLabel of(Component text) {
        return new QuantityLabel(text);
    }

    public QuantityLabel scale(float scale) {
        this.scale = scale;

        // Important: Scaling changes the effective size, so notify the parent to re-layout
        this.notifyParentIfMounted();
        return this;
    }

    // 5. Override draw() to apply scaling transformation
    @Override
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        var matrices = context.getMatrixStack();

        matrices.pushMatrix();

        // Apply scale transformation
        // We translate to the component's position, scale, and then translate back
        // to render the text at its intended location, scaled from the component's (x, y).
        matrices.translate(this.x, this.y);
        matrices.scale(this.scale, this.scale);
        matrices.translate(-this.x, -this.y);

        // Call the super method to draw the actual text.
        // The super.draw() logic for positioning is correct, but it now operates
        // within the scaled coordinate system.
        super.draw(context, mouseX, mouseY, partialTicks, delta);

        matrices.popMatrix();
    }

    // 6. Override content sizing to account for scale
    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        // Calculate the unscaled size first, then multiply by the scale.
        // We call super but pass the sizing, not 'this.horizontalSizing.get()', to avoid infinite recursion.
        // We need the unscaled size to correctly determine if wrapping is needed.
        return (int) (super.determineHorizontalContentSize(sizing) * this.scale);
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        // Calculate the unscaled size first, then multiply by the scale.
        return (int) (super.determineVerticalContentSize(sizing) * this.scale);
    }
}
