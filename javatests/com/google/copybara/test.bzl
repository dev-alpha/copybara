def all_tests(tests, deps):
  for file in tests:
    relative_target = file[:-5]
    suffix = relative_target.replace("/", ".")
    pos = PACKAGE_NAME.rfind("javatests/") + len("javatests/")
    test_class = PACKAGE_NAME[pos:].replace('/', '.',) + '.' + suffix
    native.java_test(
      name = file[:-5],
      srcs = [file],
      test_class = test_class,
      deps = deps,
    )
