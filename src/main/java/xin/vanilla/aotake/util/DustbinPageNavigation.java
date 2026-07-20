package xin.vanilla.aotake.util;

/**
 * 垃圾箱翻页边界计算，客户端显示与服务端执行共用同一规则。
 */
public final class DustbinPageNavigation {
    private DustbinPageNavigation() {
    }

    public static int targetPage(int currentPage, int totalPages, int offset) {
        if (currentPage < 1 || totalPages < 1) return -1;
        long target = (long) currentPage + offset;
        return target >= 1 && target <= totalPages ? (int) target : -1;
    }

    public static boolean canNavigate(int currentPage, int totalPages, int offset) {
        return targetPage(currentPage, totalPages, offset) > 0;
    }
}
