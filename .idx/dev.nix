{ pkgs, ... }: {
  channel = "stable-23.11";

  packages = [
    pkgs.jdk17
    pkgs.gradle
  ];

  idx = {
    extensions = [
      "mathiasfrohlich.Kotlin"
      "naco-siren.gradle-language"
    ];

    workspace = {
      onCreate = {
        default.openFiles = [ "app/src/main/java/com/stukachoff/ui/verify/VerifyScreen.kt" ];
      };
    };

    previews = {
      enable = true;
      previews = {
        android = {
          command = [
            "gradle"
            "assembleCoreDebug"
          ];
          manager = "gradle";
        };
      };
    };
  };
}
