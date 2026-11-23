// Runs inside the Shizuku process (shell or root uid). Deliberately narrow: no generic exec,
// only the one command Florsent needs, built by the service from validated arguments.
package app.florsent.service.shizuku;

interface IShellService {
    /** Reserved by Shizuku: called to shut the service process down. */
    void destroy() = 16777114;

    /** `cmd color_display set-layer-saturation <level> <caller> <target>`; 0 on success. */
    int setLayerSaturation(int level, String callerPackage, String targetPackage) = 1;
}
