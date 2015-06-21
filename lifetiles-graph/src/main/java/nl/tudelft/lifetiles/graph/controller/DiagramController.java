package nl.tudelft.lifetiles.graph.controller;

import javafx.scene.Group;
import javafx.scene.Node;
import nl.tudelft.lifetiles.graph.model.StackedMutationContainer;
import nl.tudelft.lifetiles.graph.view.DiagramView;

public class DiagramController {

    /**
     * The model of the diagram.
     */
    private StackedMutationContainer model;

    /**
     * The view of the diagram.
     */
    private DiagramView view;

    public DiagramController(DiagramView view) {
        this.view = view;
    }

    public void setDiagram(StackedMutationContainer stackedMutationContainer) {
        model = stackedMutationContainer;

    }

    public StackedMutationContainer getModel() {
        return model;
    }

    public Group drawDiagram(int zoomLevel, double width) {
        return view.drawDiagram(model, zoomLevel, width);
    }

}
