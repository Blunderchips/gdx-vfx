package com.crashinvaders.vfx.common.lml.tags;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.crashinvaders.vfx.common.scene2d.TransformScalableWrapper;
import com.github.czyzby.lml.parser.LmlParser;
import com.github.czyzby.lml.parser.impl.tag.AbstractGroupLmlTag;
import com.github.czyzby.lml.parser.tag.LmlActorBuilder;
import com.github.czyzby.lml.parser.tag.LmlTag;
import com.github.czyzby.lml.parser.tag.LmlTagProvider;

public class TransformScalableWrapperLmlTag extends AbstractGroupLmlTag {

    public TransformScalableWrapperLmlTag(LmlParser parser, LmlTag parentTag, StringBuilder rawTagData) {
        super(parser, parentTag, rawTagData);
    }

    @Override
    protected Group getNewInstanceOfGroup(LmlActorBuilder builder) {
        TransformScalableWrapper<Actor> wrapper = new TransformScalableWrapper<>();
        return wrapper;
    }

    /** @param child will be set as wrapper's child. */
    protected void addChild(final Actor child) {
        TransformScalableWrapper<Actor> wrapper = getWrapper();
        if (wrapper.getActor() != null) {
            getParser().throwErrorIfStrict("TransformScalableWrapper widget can manage only one child.");
        }
        wrapper.setActor(child);
    }

    /** @return casted actor. */
    @SuppressWarnings("unchecked")
    protected TransformScalableWrapper<Actor> getWrapper() {
        return (TransformScalableWrapper<Actor>) getActor();
    }

    public static class TagProvider implements LmlTagProvider {
        @Override
        public LmlTag create(final LmlParser parser, final LmlTag parentTag, final StringBuilder rawTagData) {
            return new TransformScalableWrapperLmlTag(parser, parentTag, rawTagData);
        }
    }
}
