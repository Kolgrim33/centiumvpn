package org.centium.vpn.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.centium.vpn.R
import org.centium.vpn.databinding.FragmentDiagnosticsBinding
import org.centium.vpn.diagnostics.DiagnosticSuite

class DiagnosticsFragment : Fragment() {

    private var _binding: FragmentDiagnosticsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiagnosticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRunAudit.setOnClickListener {
            runDiagnostics()
        }

        runDiagnostics()
    }

    private fun runDiagnostics() {
        binding.pbAuditLoading.visibility = View.VISIBLE
        binding.btnRunAudit.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val suite = DiagnosticSuite(requireContext())
            val result = suite.runFullDiagnostics()

            binding.pbAuditLoading.visibility = View.GONE
            binding.btnRunAudit.isEnabled = true

            binding.tvAuditSummary.text = "${result.passedCount}/10 Layers Operational (${result.latencyMs}ms)"

            binding.llLayerResults.removeAllViews()

            for (layer in result.layerDetails) {
                val itemView = layoutInflater.inflate(R.layout.item_diagnostic_layer, binding.llLayerResults, false)
                val tvTitle = itemView.findViewById<TextView>(R.id.tvLayerTitle)
                val tvDesc = itemView.findViewById<TextView>(R.id.tvLayerDesc)
                val tvBadge = itemView.findViewById<TextView>(R.id.tvLayerBadge)

                tvTitle.text = "Layer ${layer.layerNumber}: ${layer.layerName}"
                tvDesc.text = layer.description

                if (layer.passed) {
                    tvBadge.text = "PASS"
                    tvBadge.setTextColor(requireContext().getColor(R.color.status_connected))
                } else {
                    tvBadge.text = "FAIL"
                    tvBadge.setTextColor(requireContext().getColor(R.color.status_error))
                }

                binding.llLayerResults.addView(itemView)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
