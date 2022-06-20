modtype = LIB

apis(
  projects.kj.kjlib,
  projects.kj.auto,
  projects.kj.kjlib.kjlibSocket
)

implementations(
  projects.kj.json,
  libs.kotlinx.coroutines,
  projects.kj.reflect,
  projects.kj.async,
  projects.k.key
)