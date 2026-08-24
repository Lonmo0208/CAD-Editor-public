package com.github.rinorsi.cadeditor.common.network;

import com.github.rinorsi.cadeditor.common.EditorType;
import net.minecraft.network.FriendlyByteBuf;

public abstract class AbstractEditorUpdate<REQ, RES> {
    private EditorType editorType;
    private REQ requestData;
    private RES responseData;

    protected AbstractEditorUpdate() {
    }

    protected AbstractEditorUpdate(EditorType editorType, REQ requestData, RES responseData) {
        this.editorType = editorType;
        this.requestData = requestData;
        this.responseData = responseData;
    }

    public EditorType getEditorType() {
        return editorType;
    }

    protected void setEditorType(EditorType editorType) {
        this.editorType = editorType;
    }

    protected REQ getRequestData() {
        return requestData;
    }

    protected void setRequestData(REQ requestData) {
        this.requestData = requestData;
    }

    protected RES getResponseData() {
        return responseData;
    }

    protected void setResponseData(RES responseData) {
        this.responseData = responseData;
    }

    protected static abstract class Serializer<T extends AbstractEditorUpdate<REQ, RES>, REQ, RES> implements ImprovedPacketSerializer<T> {
        @Override
        public void write(T obj, FriendlyByteBuf buf) {
            buf.writeEnum(obj.getEditorType());
            getRequestDataSerializer().write(obj.getRequestData(), buf);
            getResponseDataSerializer().write(obj.getResponseData(), buf);

        }

        @Override
        public void read(T obj, FriendlyByteBuf buf) {
            obj.setEditorType(buf.readEnum(EditorType.class));
            obj.setRequestData(getRequestDataSerializer().read(buf));
            obj.setResponseData(getResponseDataSerializer().read(buf));
        }

        protected abstract PacketSerializer<REQ> getRequestDataSerializer();

        protected abstract PacketSerializer<RES> getResponseDataSerializer();
    }
}
