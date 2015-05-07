package password.pwm.http.servlet.peoplesearch;

class PhotoDataBean {
    private final String mimeType;
    private byte[] contents;

    PhotoDataBean(String mimeType, byte[] contents) {
        this.mimeType = mimeType;
        this.contents = contents;
    }

    public String getMimeType() {
        return mimeType;
    }

    public byte[] getContents() {
        return contents;
    }
}
