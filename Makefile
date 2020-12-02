.PHONY: test

test:
	clj -A:test -M -m saturn.test

nrepl:
	clj -A:pg:test:repl -M -m nrepl.cmdline --middleware '["cider.nrepl/cider-middleware"]'

deploy:
	lein deploy clojars
